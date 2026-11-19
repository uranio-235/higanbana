package local.higanbana.infrastructure.memory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import local.higanbana.domain.memory.MemoryMatch;
import local.higanbana.domain.memory.MemoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class DuckDbMemoryRepository implements MemoryRepository {
    private final JdbcTemplate jdbc;

    public DuckDbMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean contains(String scope, String contentHash) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM memory_entry WHERE scope = ? AND content_hash = ?)",
                Boolean.class, scope, contentHash));
    }

    @Override
    public void remember(String scope, String content, String contentHash, float[] embedding) {
        jdbc.update("""
                INSERT INTO memory_entry (id, scope, content_hash, content, embedding)
                VALUES (nextval('memory_id_seq'), ?, ?, ?, CAST(? AS FLOAT[384]))
                ON CONFLICT (scope, content_hash) DO NOTHING
                """, scope, contentHash, content, vectorLiteral(embedding));
    }

    @Override
    public List<MemoryMatch> search(String scope, float[] embedding, int limit) {
        return jdbc.query("""
                SELECT id, content, array_cosine_similarity(embedding, CAST(? AS FLOAT[384])) AS similarity
                FROM memory_entry
                WHERE scope = ?
                ORDER BY similarity DESC, id
                LIMIT ?
                """, (result, row) -> new MemoryMatch(result.getLong("id"), result.getString("content"),
                result.getDouble("similarity")), vectorLiteral(embedding), scope, limit);
    }

    private String vectorLiteral(float[] embedding) {
        return Objects.requireNonNull(java.util.Arrays.stream(toDoubleArray(embedding)).boxed()
                .map(String::valueOf).collect(Collectors.joining(",", "[", "]")));
    }

    private double[] toDoubleArray(float[] embedding) {
        double[] values = new double[embedding.length];
        for (int index = 0; index < embedding.length; index++) { values[index] = embedding[index]; }
        return values;
    }
}