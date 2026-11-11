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

    @org.junit.jupiter.api.BeforeEach
    void stubStudioPrompt() {
        doReturn(new local.higanbana.domain.ai.CachedText("Propuesta de prueba.", "test-key"))
                .when(text).studioPrompt(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @Transactional
    void uploadedImagesAndPromptSnapshotsPersistInDuckDb() throws Exception {
        Novel novel = workflow.createNovel("Imagen externa", "Aiko entra al faro.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        Panel panel = new Panel();
        panel.novelId = novel.id;
        panel.chunkId = chunk.id;
        panel.chunkPosition = 1;
        panel.position = 1;
        panel.imagePrompt = "Aiko entra al faro.";
        em.persist(panel);
        em.flush();
        var draft = workflow.createDraft(novel.id, java.util.List.of(panel.id), "");
        var output = new java.io.ByteArrayOutputStream();
        var image = new java.awt.image.BufferedImage(1200, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(1);
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                image.setRGB(column, row, random.nextInt(0x1000000));
            }
        }
        javax.imageio.ImageIO.write(image, "png", output);
        assertThat(output.size()).isGreaterThan(2_000_000);
        var first = workflow.uploadImage(novel.id, draft.id, output.toByteArray());
        em.flush();
        em.clear();
        workflow.saveDraft(novel.id, draft.id, "Una vista nueva del faro.");
        var second = workflow.uploadImage(novel.id, draft.id, output.toByteArray());
        em.flush();
        em.clear();
        assertThat(workflow.candidate(novel.id, first.id).prompt).contains("manga japones en blanco y negro", "Aiko entra al faro.");
        assertThat(workflow.candidate(novel.id, second.id).prompt).isEqualTo("Una vista nueva del faro.");
        assertThat(workflow.candidateImage(novel.id, second.id).mediaType()).isEqualTo("image/png");
    }

    @Test void jpaCreatesAndReadsRowsUsingDuckDbSequence() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("DuckDB");
        }

        Novel novel = workflow.createNovel("Primera novela DuckDB", "Una escena junto al lago.");
        assertThat(novel.id).isPositive();
        assertThat(workflow.novel(novel.id).source).isEqualTo("Una escena junto al lago.");
    }

    @Test
    @Transactional
    void deletingDraftEvictsOnlyItsStudioResponse() {
        Novel novel = workflow.createNovel("Cache de estudio", "Una ventana bajo la lluvia.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        Panel panel = new Panel();
        panel.novelId = novel.id;
        panel.chunkId = chunk.id;
        panel.chunkPosition = 1;
        panel.position = 1;
        panel.imagePrompt = "Una ventana bajo la lluvia.";
        em.persist(panel);
        var draft = workflow.createDraft(novel.id, java.util.List.of(panel.id), "");
        cache.store(draft.aiCacheKey, "studio-image-prompt-v1", "https://provider.test", "model", "v1", "Respuesta", Duration.ofDays(1));
        cache.store("other-key", "studio-image-prompt-v1", "https://provider.test", "model", "v1", "Otra", Duration.ofDays(1));
        em.flush();
        em.clear();
        workflow.deleteDraft(novel.id, draft.id);
        em.flush();
        assertThat(cache.find("test-key")).isEmpty();
        assertThat(cache.find("other-key")).contains("Otra");
        assertThat(workflow.drafts(novel.id)).isEmpty();
    }

        @Test
        @Transactional
        void deletingAiDraftForcesARealProviderRequestNextTime() throws Exception {
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String firstResponse = mapper.writeValueAsString(java.util.Map.of("id", "first", "object", "chat.completion", "created", 1,
            "model", "test-model", "choices", java.util.List.of(java.util.Map.of("index", 0, "finish_reason", "stop",
            "message", java.util.Map.of("role", "assistant", "content", "{\"prompt\":\"Primera propuesta.\"}")))));
        String secondResponse = mapper.writeValueAsString(java.util.Map.of("id", "second", "object", "chat.completion", "created", 1,
            "model", "test-model", "choices", java.util.List.of(java.util.Map.of("index", 0, "finish_reason", "stop",
            "message", java.util.Map.of("role", "assistant", "content", "{\"prompt\":\"Segunda propuesta.\"}")))));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://provider.test/v1/chat/completions"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(firstResponse, org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://provider.test/v1/chat/completions"))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(secondResponse, org.springframework.http.MediaType.APPLICATION_JSON));
        var realAssistant = new TextAssistant("nvidia", "https://provider.test", "test-key", "test-model", mapper, builder, cache, Duration.ofDays(1));
        org.mockito.Mockito.doAnswer(invocation -> realAssistant.studioPrompt(invocation.getArgument(0)))
            .when(text).studioPrompt(org.mockito.ArgumentMatchers.anyString());

        Novel novel = workflow.createNovel("Regenerar", "Una ventana bajo la lluvia.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        Panel panel = new Panel();
        panel.novelId = novel.id;
        panel.chunkId = chunk.id;
        panel.chunkPosition = 1;
        panel.position = 1;
        panel.imagePrompt = "La ventana bajo la lluvia.";
        em.persist(panel);

        var first = workflow.createDraft(novel.id, java.util.List.of(panel.id), "");
        var same = workflow.createDraft(novel.id, java.util.List.of(panel.id), "");
        assertThat(first.prompt).startsWith("Primera propuesta.");
        assertThat(same.prompt).isEqualTo(first.prompt);
        assertThat(same.aiCacheKey).isEqualTo(first.aiCacheKey);
        em.flush();
        em.clear();
        workflow.deleteDraft(novel.id, first.id);
        em.flush();
        var refreshed = workflow.createDraft(novel.id, java.util.List.of(panel.id), "");
        assertThat(refreshed.prompt).startsWith("Segunda propuesta.");
        assertThat(workflow.drafts(novel.id)).hasSize(2);
        server.verify();
        }

    @Test
    @Transactional
    void multiplePanelDraftAndApprovalOverlapPersistInDuckDb() throws Exception {
        Novel novel = workflow.createNovel("Escena completa", "Una ventana oscura bajo la lluvia.");
        Chunk chunk = new Chunk();
        chunk.novelId = novel.id;
        chunk.position = 1;
        chunk.content = novel.source;
        em.persist(chunk);
        var panels = new java.util.ArrayList<Panel>();
        for (int position = 1; position <= 3; position++) {
            Panel panel = new Panel();
            panel.novelId = novel.id;
            panel.chunkId = chunk.id;
            panel.chunkPosition = 1;
            panel.position = position;
            panel.imagePrompt = "Viñeta " + position;
            em.persist(panel);
            panels.add(panel);
        }
        em.flush();
        var draft = workflow.createDraft(novel.id, panels.stream().map(panel -> panel.id).toList(), "");
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        var candidate = workflow.uploadImage(novel.id, draft.id, output.toByteArray());
        em.flush();
        em.clear();
        assertThat(em.find(local.higanbana.domain.StudioDraft.class, draft.id).panelIds)
                .containsExactlyElementsOf(panels.stream().map(panel -> panel.id).toList());
        workflow.approve(novel.id, candidate.id);
        em.flush();
        em.clear();
        workflow.deleteDraft(novel.id, draft.id);
        em.flush();
        em.clear();
        assertThat(workflow.drafts(novel.id)).isEmpty();
        assertThat(workflow.candidate(novel.id, candidate.id).draftId).isNull();
        assertThat(workflow.candidateImage(novel.id, candidate.id).mediaType()).isEqualTo("image/png");
        var duplicateDraft = workflow.createDraft(novel.id, java.util.List.of(panels.get(2).id), "");
        var duplicate = workflow.uploadImage(novel.id, duplicateDraft.id, output.toByteArray());
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> workflow.approve(novel.id, duplicate.id));
    }

    @Test void aiResponsesPersistInDuckDbAndExpiredEntriesAreIgnored() {
        cache.store("a".repeat(64), "describe-v1", "https://provider.test", "model-a", "prompt-v1",
                "Una descripcion reutilizable.", Duration.ofDays(30));
        assertThat(cache.find("a".repeat(64))).contains("Una descripcion reutilizable.");
        cache.store("a".repeat(64), "describe-v1", "https://provider.test", "model-a", "prompt-v1",
            "Descripcion actualizada.", Duration.ofDays(30));
        assertThat(cache.find("a".repeat(64))).contains("Descripcion actualizada.");

        cache.store("c".repeat(64), "describe-v1", "https://provider.test", "model-a", "prompt-v1",
            "Otra descripcion.", Duration.ofDays(30));
        cache.evict("a".repeat(64));
        assertThat(cache.find("a".repeat(64))).isEmpty();
        assertThat(cache.find("c".repeat(64))).contains("Otra descripcion.");

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