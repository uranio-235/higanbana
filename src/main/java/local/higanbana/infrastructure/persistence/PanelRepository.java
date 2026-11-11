package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.Panel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PanelRepository extends JpaRepository<Panel, Long> {
    @Query("SELECT panel FROM Panel panel WHERE panel.chunkId = :chunkId ORDER BY panel.position")
    List<Panel> findPanelsForChunk(@Param("chunkId") long chunkId);

    @Query("SELECT panel FROM Panel panel WHERE panel.novelId = :novelId ORDER BY panel.chunkPosition, panel.position")
    List<Panel> findPanelsForNovel(@Param("novelId") long novelId);
}
