package local.higanbana.application;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import local.higanbana.domain.entity.MangaEntry;
import local.higanbana.domain.export.MangaPdfGenerator;
import local.higanbana.domain.export.PanelImageBuilder;
import local.higanbana.domain.image.ApprovedImageStorage;
import local.higanbana.domain.workflow.WorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MangaPdfExportService {
    private final WorkflowRepository repository;
    private final ApprovedImageStorage imageStorage;
    private final MangaPdfGenerator pdfGenerator;

    public MangaPdfExportService(
            WorkflowRepository repository,
            ApprovedImageStorage imageStorage,
            MangaPdfGenerator pdfGenerator) {
        this.repository = repository;
        this.imageStorage = imageStorage;
        this.pdfGenerator = pdfGenerator;
    }

    public byte[] export(long novelId) {
        var novel = repository.findNovel(novelId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro la novela."));
        List<MangaEntry> entries = repository.findEntries(novelId);
        if (entries.isEmpty() && novel.coverFilename == null) {
            throw new IllegalStateException("La novela no tiene imagenes aprobadas para exportar.");
        }

        List<MangaPdfGenerator.PanelImage> panels = new ArrayList<>();
        if (novel.coverFilename != null) {
            panels.add(image(novel.coverFilename, novel.coverMediaType));
        }
        entries.forEach(entry -> panels.add(image(entry.filename, entry.mediaType)));
        return pdfGenerator.generate(panels);
    }

    private MangaPdfGenerator.PanelImage image(String filename, String mediaType) {
        try {
            return new PanelImageBuilder().bytes(imageStorage.read(filename)).mediaType(mediaType).build();
        } catch (IOException exception) {
            throw new IllegalStateException("No se encuentra una imagen aprobada necesaria para el PDF.", exception);
        }
    }
}
