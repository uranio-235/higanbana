package local.higanbana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.sql.DataSource;
import local.higanbana.application.MemoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class MemoryRagIntegrationTest {
    @TempDir static Path databaseDirectory;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:duckdb:" + databaseDirectory.resolve("memory-test.duckdb"));
    }

    @Autowired DataSource dataSource;
    @Autowired MemoryService memories;
    @Value("${app.memory.onnx-path}") String onnxPath;
    @Value("${app.memory.tokenizer-path}") String tokenizerPath;

    @Test
    void storesThreeLocallyEmbeddedMemoriesAndSearchesThemByDuckDbCosineSimilarity() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of(onnxPath)) && Files.isRegularFile(Path.of(tokenizerPath)),
                "Run memory-model/download-model.ps1 or configure local model paths.");
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("DuckDB");
        }

        String scope = "rag-integration-" + UUID.randomUUID();
        String exact = "Una joven de cabello oscuro camina bajo la lluvia.";
        String paraphrase = "Una muchacha de pelo negro anda bajo la lluvia.";
        String unrelated = "Un avion cruza el cielo sobre el oceano.";
        memories.remember(scope, exact);
        memories.remember(scope, paraphrase);
        memories.remember(scope, unrelated);

        var matches = memories.search(scope, exact, 3);

        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(match -> match.content())
                .containsExactly(exact, paraphrase, unrelated);
        assertThat(matches.getFirst().similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(matches.get(1).similarity()).isGreaterThan(matches.get(2).similarity());
        assertThat(matches).allSatisfy(match -> assertThat(match.similarity()).isBetween(-1.0, 1.0));
    }
}