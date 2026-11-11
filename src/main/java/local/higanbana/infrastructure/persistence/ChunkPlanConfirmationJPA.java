package local.higanbana.infrastructure.persistence;

import java.util.Optional;
import local.higanbana.domain.entity.ChunkPlanConfirmation;
import org.springframework.stereotype.Component;

@Component
class ChunkPlanConfirmationJPA {
    private final ChunkPlanConfirmationRepository repository;

    ChunkPlanConfirmationJPA(ChunkPlanConfirmationRepository repository) { this.repository = repository; }

    Optional<ChunkPlanConfirmation> findById(long id) { return repository.findById(id); }
    void save(ChunkPlanConfirmation confirmation) { repository.save(confirmation); }
}
