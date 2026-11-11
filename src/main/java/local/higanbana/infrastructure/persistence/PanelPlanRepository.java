package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.PanelPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PanelPlanRepository extends JpaRepository<PanelPlan, Long> {
    @Query("SELECT plan FROM PanelPlan plan WHERE plan.chunkId = :chunkId")
    List<PanelPlan> findPlansForChunk(@Param("chunkId") long chunkId);
}
