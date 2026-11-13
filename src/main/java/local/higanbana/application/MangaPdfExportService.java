package local.higanbana.application;

import java.io.IOException;
import java.util.List;
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
        repository.findNovel(novelId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro la novela."));
        List<MangaEntry> entries = repository.findEntries(novelId);
        if (entries.isEmpty()) {
            throw new IllegalStateException("La novela no tiene imagenes aprobadas para exportar.");
        }

        List<MangaPdfGenerator.PanelImage> panels = entries.stream()
                .map(entry -> {
                    try {
                        return new PanelImageBuilder().bytes(imageStorage.read(entry.filename))
                            .mediaType(entry.mediaType).build();
                    } catch (IOException exception) {
                        throw new IllegalStateException("No se encuentra una imagen aprobada necesaria para el PDF.", exception);
                    }
                })
                .toList();
        return pdfGenerator.generate(panels);
    }
}
