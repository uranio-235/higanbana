package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    @Query("SELECT chunk FROM Chunk chunk WHERE chunk.novelId = :novelId ORDER BY chunk.position")
    List<Chunk> findChunksForNovel(@Param("novelId") long novelId);
}
