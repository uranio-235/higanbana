package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.PanelPlan;
import org.springframework.stereotype.Component;

@Component
class PanelPlanJPA {
    private final PanelPlanRepository repository;

    PanelPlanJPA(PanelPlanRepository repository) { this.repository = repository; }

    List<PanelPlan> findPlansForChunk(long chunkId) { return repository.findPlansForChunk(chunkId); }
    void save(PanelPlan plan) { repository.save(plan); }
    void deleteAndFlush(PanelPlan plan) {
        repository.delete(plan);
        repository.flush();
    }
}
