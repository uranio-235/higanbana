package local.higanbana.infrastructure.persistence;

import local.higanbana.domain.entity.ChunkPlanConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkPlanConfirmationRepository extends JpaRepository<ChunkPlanConfirmation, Long> {}
