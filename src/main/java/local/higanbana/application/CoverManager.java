package local.higanbana.application;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import local.higanbana.domain.entity.Novel;
import local.higanbana.domain.image.ApprovedImageStorage;
import local.higanbana.domain.workflow.WorkflowRepository;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.infrastructure.image.ImageProvider;
import local.higanbana.infrastructure.image.ImageResultBuilder;
import local.higanbana.infrastructure.image.UploadedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class CoverManager {
    private static final Logger log = LoggerFactory.getLogger(CoverManager.class);
    private final WorkflowRepository repository;
    private final TextAssistant text;
    private final ApprovedImageStorage storage;

    public CoverManager(WorkflowRepository repository, TextAssistant text, ApprovedImageStorage storage) {
        this.repository = repository;
        this.text = text;
        this.storage = storage;
    }

    public boolean available() { return text.coverPromptsAvailable(); }

    public void generate(long novelId, List<Long> chunkIds) {
        Novel novel = novel(novelId);
        if (chunkIds == null || chunkIds.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un chunk de esta novela, sin duplicados.");
        }
        var ids = new HashSet<>(chunkIds);
        if (ids.size() != chunkIds.size()) {
            throw new IllegalArgumentException("Selecciona al menos un chunk de esta novela, sin duplicados.");
        }
        var selected = repository.findChunks(novelId).stream().filter(chunk -> ids.contains(chunk.id)).toList();
        if (selected.size() != chunkIds.size()) {
            throw new IllegalArgumentException("Los chunks seleccionados no pertenecen a esta novela.");
        }
        StringBuilder context = new StringBuilder();
        selected.forEach(chunk -> context.append("Chunk ").append(chunk.position).append(":\n")
                .append(chunk.content).append("\n\n"));
        novel.coverPrompt = text.coverPrompt(novel.title, context.toString()).strip();
    }

    public void savePrompt(long novelId, String prompt) {
        if (prompt == null || prompt.isBlank() || prompt.length() > 4000) {
            throw new IllegalArgumentException("El prompt de portada es obligatorio y no puede superar 4000 caracteres.");
        }
        novel(novelId).coverPrompt = prompt.strip();
    }

    public void upload(long novelId, String prompt, byte[] bytes) {
        Novel novel = novel(novelId);
        var image = UploadedImage.decode(bytes);
        savePrompt(novelId, prompt);
        String previous = novel.coverFilename;
        String filename = UUID.randomUUID() + (image.mediaType().equals("image/png") ? ".png" : ".jpg");
        try {
            storage.save(filename, image.bytes());
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la portada en el almacenamiento local.", exception);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                String obsolete = status == STATUS_COMMITTED ? previous : filename;
                if (obsolete != null) {
                    try {
                        storage.delete(obsolete);
                    } catch (IOException exception) {
                        log.warn("No se pudo eliminar la imagen de portada obsoleta {}.", obsolete, exception);
                    }
                }
            }
        });
        novel.coverFilename = filename;
        novel.coverMediaType = image.mediaType();
    }

    @Transactional(readOnly = true)
    public ImageProvider.ImageResult image(long novelId) {
        Novel novel = novel(novelId);
        if (novel.coverFilename == null) {
            throw new IllegalArgumentException("La novela no tiene portada.");
        }
        try {
            return new ImageResultBuilder().bytes(storage.read(novel.coverFilename))
                    .mediaType(novel.coverMediaType).provenance("Imagen subida por el usuario").build();
        } catch (IOException exception) {
            throw new IllegalStateException("No se encuentra la portada en el almacenamiento local.", exception);
        }
    }

    private Novel novel(long novelId) {
        return repository.findNovel(novelId)
                .orElseThrow(() -> new IllegalArgumentException("La novela no existe."));
    }
}
