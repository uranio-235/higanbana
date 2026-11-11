package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.ChunkPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkPlanRepository extends JpaRepository<ChunkPlan, Long> {
    @Query("SELECT plan FROM ChunkPlan plan WHERE plan.novelId = :novelId")
    List<ChunkPlan> findPlansForNovel(@Param("novelId") long novelId);
}
