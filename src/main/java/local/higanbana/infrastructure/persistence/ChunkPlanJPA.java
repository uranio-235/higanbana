package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.ChunkPlan;
import org.springframework.stereotype.Component;

@Component
class ChunkPlanJPA {
    private final ChunkPlanRepository repository;

    ChunkPlanJPA(ChunkPlanRepository repository) { this.repository = repository; }

    List<ChunkPlan> findPlansForNovel(long novelId) { return repository.findPlansForNovel(novelId); }
    void save(ChunkPlan plan) { repository.save(plan); }
}
