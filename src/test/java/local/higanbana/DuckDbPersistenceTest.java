package local.higanbana;

import local.higanbana.application.Workflow;
import local.higanbana.domain.Novel;
import local.higanbana.infrastructure.ai.JdbcAiResponseCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DuckDbPersistenceTest {
    @TempDir static Path databaseDirectory;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:duckdb:" + databaseDirectory.resolve("test.duckdb"));
        registry.add("app.manga-dir", () -> databaseDirectory.resolve("manga").toString());
    }

    @Autowired DataSource dataSource;
    @Autowired Workflow workflow;
    @Autowired JdbcAiResponseCache cache;
    @Autowired JdbcTemplate jdbc;

    @Test void jpaCreatesAndReadsRowsUsingDuckDbSequence() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("DuckDB");
        }

        Novel novel = workflow.createNovel("Primera novela DuckDB", "Una escena junto al lago.");
        assertThat(novel.id).isPositive();
        assertThat(workflow.novel(novel.id).source).isEqualTo("Una escena junto al lago.");
    }

    @Test void aiResponsesPersistInDuckDbAndExpiredEntriesAreIgnored() {
        cache.store("a".repeat(64), "describe-v1", "https://provider.test", "model-a", "prompt-v1",
                "Una descripcion reutilizable.", Duration.ofDays(30));
        assertThat(cache.find("a".repeat(64))).contains("Una descripcion reutilizable.");
        cache.store("a".repeat(64), "describe-v1", "https://provider.test", "model-a", "prompt-v1",
            "Descripcion actualizada.", Duration.ofDays(30));
        assertThat(cache.find("a".repeat(64))).contains("Descripcion actualizada.");

        jdbc.update("""
            INSERT INTO ai_response_cache (cache_key, operation, provider, model, prompt_version, response, expires_at)
            VALUES (?, 'describe-v1', 'https://provider.test', 'model-a', 'prompt-v1', 'Respuesta expirada.',
                CURRENT_TIMESTAMP - INTERVAL '1 second')
            """, "b".repeat(64));
        assertThat(cache.find("b".repeat(64))).isEmpty();
    }
}