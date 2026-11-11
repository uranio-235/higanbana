package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.Panel;
import org.springframework.stereotype.Component;

@Component
class PanelJPA {
    private final PanelRepository repository;

    PanelJPA(PanelRepository repository) { this.repository = repository; }

    List<Panel> findPanelsForChunk(long chunkId) { return repository.findPanelsForChunk(chunkId); }
    List<Panel> findPanelsForNovel(long novelId) { return repository.findPanelsForNovel(novelId); }
    Optional<Panel> findById(long id) { return repository.findById(id); }
    void save(Panel panel) { repository.save(panel); }
}
