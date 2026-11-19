package local.higanbana.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import local.higanbana.domain.memory.MemoryMatch;
import local.higanbana.domain.memory.MemoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("test")
public class H2MemoryRepository implements MemoryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public H2MemoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public boolean contains(String scope, String contentHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM memory_entry WHERE scope = ? AND content_hash = ?)",
                Boolean.class, scope, contentHash));
    }

    @Override
    public void remember(String scope, String content, String contentHash, float[] embedding) {
        jdbc.update("MERGE INTO memory_entry (scope, content_hash, content, embedding) KEY (scope, content_hash) VALUES (?, ?, ?, ?)",
                scope, contentHash, content, serialize(embedding));
    }

    @Override
    public List<MemoryMatch> search(String scope, float[] embedding, int limit) {
        return jdbc.query("SELECT id, content, embedding FROM memory_entry WHERE scope = ?", (result, row) -> {
                    float[] stored = deserialize(result.getString("embedding"));
                    return new MemoryMatch(result.getLong("id"), result.getString("content"), cosine(embedding, stored));
                }, scope).stream()
                .sorted(Comparator.comparingDouble(MemoryMatch::similarity).reversed().thenComparingLong(MemoryMatch::id))
                .limit(limit).toList();
    }

    private String serialize(float[] embedding) {
        try {
            return mapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el embedding de memoria.", exception);
        }
    }

    private float[] deserialize(String embedding) {
        try {
            return mapper.readValue(embedding, float[].class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo leer el embedding de memoria.", exception);
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left.length != right.length) { throw new IllegalStateException("Dimensiones de memoria incompatibles."); }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) { return 0; }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}