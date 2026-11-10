package local.higanbana.infrastructure.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import local.higanbana.domain.ai.AiResponseCache;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcAiResponseCache implements AiResponseCache {
    private final JdbcTemplate jdbc;

    public JdbcAiResponseCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> find(String key) {
        return jdbc.query("SELECT response FROM ai_response_cache WHERE cache_key = ? AND expires_at > CURRENT_TIMESTAMP",
                result -> result.next() ? Optional.of(result.getString(1)) : Optional.empty(), key);
    }

    @Override
    public synchronized void store(String key, String operation, String provider, String model, String promptVersion,
                      String response, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        jdbc.update("DELETE FROM ai_response_cache WHERE expires_at <= CURRENT_TIMESTAMP");
        Timestamp createdAt = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(ttl));
        int updated = jdbc.update("""
            UPDATE ai_response_cache
            SET operation = ?, provider = ?, model = ?, prompt_version = ?, response = ?, created_at = ?, expires_at = ?
            WHERE cache_key = ?
            """, operation, provider, model, promptVersion, response, createdAt, expiresAt, key);
        if (updated == 0) {
            jdbc.update("""
                INSERT INTO ai_response_cache (cache_key, operation, provider, model, prompt_version, response, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, key, operation, provider, model, promptVersion, response, createdAt, expiresAt);
        }
    }
}