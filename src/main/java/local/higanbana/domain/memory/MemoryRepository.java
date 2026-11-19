package local.higanbana.domain.memory;

import java.util.List;

public interface MemoryRepository {
    boolean contains(String scope, String contentHash);
    void remember(String scope, String content, String contentHash, float[] embedding);
    List<MemoryMatch> search(String scope, float[] embedding, int limit);
}