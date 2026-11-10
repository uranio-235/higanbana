package local.higanbana;

import local.higanbana.application.Workflow;
import local.higanbana.domain.Novel;
import local.higanbana.domain.ChunkPlan;
import local.higanbana.domain.Chunk;
import local.higanbana.domain.Panel;
import local.higanbana.domain.PanelPlan;
import local.higanbana.domain.PanelPlanConfirmation;
import local.higanbana.infrastructure.ai.JdbcAiResponseCache;
import local.higanbana.infrastructure.ai.TextAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

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
    @Autowired EntityManager em;
    @MockitoSpyBean TextAssistant text;

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

    @Test
    @Transactional
    void confirmingChunksDoesNotUpdateTheDuckDbParentReferencedBySegmentRows() {
        Novel novel = workflow.createNovel("Confirmacion DuckDB", "Escena uno. Escena dos.");
        ChunkPlan plan = new ChunkPlan();
        plan.novelId = novel.id;
        plan.chunkLimit = 5000;
        plan.segments.add("Escena uno.");
        plan.segments.add(" Escena dos.");
        em.persist(plan);
        em.flush();
        em.clear();

        workflow.confirmChunks(novel.id);
        em.flush();
        em.clear();

        assertThat(workflow.chunkPlan(novel.id).confirmed).isTrue();
        assertThat(workflow.chunks(novel.id)).extracting(chunk -> chunk.content)
                .containsExactly("Escena uno.", " Escena dos.");
        workflow.confirmChunks(novel.id);
        assertThat(workflow.chunks(novel.id)).hasSize(2);
    }

    @Test
    @Transactional
    void imagePanelPromptsPersistInTheExistingDuckDbDescriptionColumns() {
        Novel novel = workflow.createNovel("Prompts visuales DuckDB", "Aiko abre la puerta del faro.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        em.flush();

        PanelPlan plan = new PanelPlan();
        plan.novelId = novel.id;
        plan.chunkId = chunk.id;
        plan.panelCount = 1;
        plan.imagePrompts.add("Aiko abre una puerta bajo la lluvia, plano medio.");
        em.persist(plan);
        Panel panel = new Panel();
        panel.novelId = novel.id;
        panel.chunkId = chunk.id;
        panel.chunkPosition = 1;
        panel.position = 1;
        panel.imagePrompt = plan.imagePrompts.getFirst();
        em.persist(panel);
        em.flush();
        em.clear();

        assertThat(workflow.panelPlan(novel.id, chunk.id).imagePrompts)
                .containsExactly("Aiko abre una puerta bajo la lluvia, plano medio.");
        assertThat(workflow.panels(novel.id).getFirst().imagePrompt)
                .isEqualTo("Aiko abre una puerta bajo la lluvia, plano medio.");
    }

    @Test
    @Transactional
    void confirmingPanelsDoesNotUpdateDuckDbParentReferencedByPromptRows() {
        Novel novel = workflow.createNovel("Confirmar paneles DuckDB", "Aiko abre el faro.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        em.flush();

        PanelPlan plan = new PanelPlan();
        plan.novelId = novel.id;
        plan.chunkId = chunk.id;
        plan.panelCount = 1;
        plan.provenance = "Prompts de imagen / NVIDIA";
        plan.imagePrompts.add("Aiko abre el faro bajo la lluvia.");
        em.persist(plan);
        em.flush();
        em.clear();

        workflow.confirmPanels(novel.id, chunk.id);
        workflow.confirmPanels(novel.id, chunk.id);
        em.flush();
        em.clear();

        assertThat(workflow.panelPlan(novel.id, chunk.id).confirmed).isTrue();
        assertThat(workflow.panels(novel.id)).singleElement()
                .extracting(panel -> panel.imagePrompt).isEqualTo("Aiko abre el faro bajo la lluvia.");
    }

    @Test
    @Transactional
    void editingAndRegeneratingPromptsDoesNotUpdateReferencedDuckDbPlan() {
        Novel novel = workflow.createNovel("Revisar prompts DuckDB", "Aiko entra y abre la puerta del faro.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        em.flush();

        PanelPlan plan = new PanelPlan();
        plan.novelId = novel.id;
        plan.chunkId = chunk.id;
        plan.panelCount = 1;
        plan.provenance = "Prompts de imagen / NVIDIA";
        plan.imagePrompts.add("Prompt original.");
        em.persist(plan);
        em.flush();
        em.clear();

        workflow.savePanelImagePrompts(novel.id, chunk.id, java.util.List.of("Prompt editado."));
        em.flush();
        em.clear();
        assertThat(workflow.panelPlan(novel.id, chunk.id).imagePrompts).containsExactly("Prompt editado.");

        doReturn(java.util.List.of("Prompt regenerado uno.", "Prompt regenerado dos."))
                .when(text).imagePanelPrompts(chunk.content, 2, "Separar acciones");
        workflow.proposePanels(novel.id, chunk.id, 2, "Separar acciones");
        em.flush();
        em.clear();

        PanelPlan regenerated = workflow.panelPlan(novel.id, chunk.id);
        assertThat(regenerated.panelCount).isEqualTo(2);
        assertThat(regenerated.imagePrompts).containsExactly("Prompt regenerado uno.", "Prompt regenerado dos.");
    }

    @Test
    @Transactional
    void confirmedLegacyDemoPromptsCanBeReplacedWithoutChangingPanelIds() {
        Novel novel = workflow.createNovel("Actualizar prompts confirmados", "Aiko espera frente al faro.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        em.flush();

        PanelPlan plan = new PanelPlan();
        plan.novelId = novel.id;
        plan.chunkId = chunk.id;
        plan.panelCount = 1;
        plan.provenance = "DEMO local - sin IA";
        plan.imagePrompts.add("Prompt antiguo de demo.");
        em.persist(plan);
        Panel panel = new Panel();
        panel.novelId = novel.id;
        panel.chunkId = chunk.id;
        panel.chunkPosition = 1;
        panel.position = 1;
        panel.imagePrompt = "Prompt antiguo de demo.";
        em.persist(panel);
        em.persist(new PanelPlanConfirmation(plan.id, plan.provenance));
        em.flush();
        em.clear();
        Long panelId = workflow.panels(novel.id).getFirst().id;

        doReturn(java.util.List.of("Prompt NVIDIA nuevo.")).when(text)
                .imagePanelPrompts(chunk.content, 1, "Actualizar");
        workflow.proposePanels(novel.id, chunk.id, 1, "Actualizar");
        em.flush();
        em.clear();

        PanelPlan updated = workflow.panelPlan(novel.id, chunk.id);
        assertThat(updated.confirmed).isTrue();
        assertThat(updated.provenance).isEqualTo("Prompts de imagen / NVIDIA");
        assertThat(updated.imagePrompts).containsExactly("Prompt NVIDIA nuevo.");
        assertThat(workflow.panels(novel.id).getFirst().id).isEqualTo(panelId);
        assertThat(workflow.panels(novel.id).getFirst().imagePrompt).isEqualTo("Prompt NVIDIA nuevo.");
    }
}