package local.higanbana.infrastructure.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import local.higanbana.domain.memory.MemoryMatch;
import local.higanbana.domain.memory.MemoryRepository;

public final class InMemoryMemoryRepository implements MemoryRepository {
    private final Map<String, MemoryMatch> memories = new LinkedHashMap<>();
    private final Map<Long, float[]> embeddings = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized boolean contains(String scope, String contentHash) {
        return memories.containsKey(scope + ":" + contentHash);
    }

    @Override
    public synchronized void remember(String scope, String content, String contentHash, float[] embedding) {
        String key = scope + ":" + contentHash;
        if (memories.containsKey(key)) { return; }
        long id = nextId++;
        memories.put(key, new MemoryMatch(id, content, 0));
        embeddings.put(id, embedding.clone());
    }

    @Override
    public synchronized List<MemoryMatch> search(String scope, float[] embedding, int limit) {
        List<MemoryMatch> matches = new ArrayList<>();
        for (var entry : memories.entrySet()) {
            if (!entry.getKey().startsWith(scope + ":")) { continue; }
            MemoryMatch memory = entry.getValue();
            matches.add(new MemoryMatch(memory.id(), memory.content(), cosine(embedding, embeddings.get(memory.id()))));
        }
        return matches.stream().sorted(Comparator.comparingDouble(MemoryMatch::similarity).reversed()
                .thenComparingLong(MemoryMatch::id)).limit(limit).toList();
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}