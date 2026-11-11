package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.Chunk;
import org.springframework.stereotype.Component;

@Component
class ChunkJPA {
    private final ChunkRepository repository;

    ChunkJPA(ChunkRepository repository) { this.repository = repository; }

    List<Chunk> findChunksForNovel(long novelId) { return repository.findChunksForNovel(novelId); }
    Optional<Chunk> findById(long id) { return repository.findById(id); }
    void save(Chunk chunk) { repository.save(chunk); }
}
