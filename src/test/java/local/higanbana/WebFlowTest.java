package local.higanbana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import local.higanbana.domain.Chunking;
import local.higanbana.application.Workflow;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.infrastructure.ai.JpaAiResponseCache;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.nvidia.api-key=test-key", "app.text.model=meta/llama-3.1-8b-instruct", "app.image.model="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebFlowTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.manga-dir", () -> storage.toString());
        registry.add("app.text-mode", () -> "demo");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:webflow;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired Workflow workflow;
        @org.springframework.boot.test.web.server.LocalServerPort int port;
        @MockitoSpyBean TextAssistant text;
    @Autowired org.springframework.core.env.Environment environment;
        @Autowired javax.sql.DataSource dataSource;
        @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
        @Autowired liquibase.integration.spring.SpringLiquibase migrations;
        @Autowired JpaAiResponseCache aiResponseCache;
    MockHttpSession session;

        @Test void testsUseH2WithIdempotentLiquibaseMigrationsAndHibernateValidation() throws Exception {
                try (var connection = dataSource.getConnection()) {
                        assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
                }
                assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = ?", Integer.class,
                                "001-initial-schema")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = ?", Integer.class,
                                "005-panel-plan-confirmation")).isEqualTo(1);
                migrations.afterPropertiesSet();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = ?", Integer.class,
                                "001-initial-schema")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = ?", Integer.class,
                                "005-panel-plan-confirmation")).isEqualTo(1);
        }

    @BeforeEach void session() throws Exception {
        doAnswer(invocation -> Chunking.split(invocation.getArgument(0), invocation.getArgument(1)))
                .when(text).chunks(anyString(), anyInt(), anyString());
        doAnswer(invocation -> java.util.stream.IntStream.rangeClosed(1, invocation.getArgument(1))
                .mapToObj(index -> "Prompt visual de prueba " + index + ": accion, personaje y encuadre.").toList())
                .when(text).imagePanelPrompts(anyString(), anyInt(), anyString());
        doReturn(new local.higanbana.domain.ai.CachedText("Propuesta de prueba.", "test-key"))
                .when(text).studioPrompt(anyString());
        session = (MockHttpSession) mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nueva obra"))).andReturn().getRequest().getSession();
    }

    private MockHttpServletRequestBuilder form(String path) {
        return post(path).session(session).param("_csrf", session.getAttribute("csrf").toString());
    }

        @Test void realHttpUploadAcceptsLargePngAndReportsOversizeWithoutReset() throws Exception {
                var novel = workflow.createNovel("Subida HTTP", "La lluvia golpea la ventana.");
                workflow.proposeChunks(novel.id, 5000, "");
                workflow.confirmChunks(novel.id);
                var chunk = workflow.chunks(novel.id).getFirst();
                workflow.proposePanels(novel.id, chunk.id, 1, "");
                workflow.confirmPanels(novel.id, chunk.id);
                var draft = workflow.createDraft(novel.id, List.of(workflow.panels(novel.id).getFirst().id), "");
                var image = new java.awt.image.BufferedImage(1200, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var random = new java.util.Random(1);
                for (int row = 0; row < image.getHeight(); row++) {
                        for (int column = 0; column < image.getWidth(); column++) {
                                image.setRGB(column, row, random.nextInt(0x1000000));
                        }
                }
                var output = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "png", output);
                assertThat(output.size()).isGreaterThan(2_000_000);

                var client = java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
                String base = "http://127.0.0.1:" + port + "/novels/" + novel.id;
                var page = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/studio")).GET().build(),
                                java.net.http.HttpResponse.BodyHandlers.ofString());
                var csrfMatch = java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
                assertThat(csrfMatch.find()).isTrue();
                String csrf = csrfMatch.group(1);
                String url = base + "/studio/" + draft.id + "/upload";
                var success = uploadOverHttp(client, url, csrf, output.toByteArray());
                assertThat(success.statusCode()).isEqualTo(302);
                assertThat(workflow.candidates(novel.id)).hasSize(1);
                var oversized = uploadOverHttp(client, url, csrf, new byte[23_000_000]);
                assertThat(oversized.statusCode()).isEqualTo(400);
                assertThat(oversized.body()).contains("limite de 20 MB");
                assertThat(workflow.candidates(novel.id)).hasSize(1);
        }

        private java.net.http.HttpResponse<String> uploadOverHttp(java.net.http.HttpClient client, String url,
                                                                                                                           String csrf, byte[] image) throws Exception {
                String boundary = "higanbana-test-boundary";
                String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf
                                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"prompt\"\r\n\r\nPrompt HTTP"
                                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"panel.png\"\r\n"
                                + "Content-Type: image/png\r\n\r\n";
                String suffix = "\r\n--" + boundary + "--\r\n";
                var body = java.net.http.HttpRequest.BodyPublishers.concat(
                                java.net.http.HttpRequest.BodyPublishers.ofString(prefix),
                                java.net.http.HttpRequest.BodyPublishers.ofByteArray(image),
                                java.net.http.HttpRequest.BodyPublishers.ofString(suffix));
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build();
                return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        }

        private MockMultipartFile image() throws Exception {
                var output = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
                return new MockMultipartFile("image", "panel.png", "image/png", output.toByteArray());
        }

        @Test void studioAcceptsAllPanelsOfAChunkAndIncludesTheOriginalScene() throws Exception {
                var novel = workflow.createNovel("Escena", "La lampara roja ilumina la ventana bajo la lluvia.");
                workflow.proposeChunks(novel.id, 5000, "");
                workflow.confirmChunks(novel.id);
                var chunk = workflow.chunks(novel.id).getFirst();
                workflow.proposePanels(novel.id, chunk.id, 3, "");
                workflow.confirmPanels(novel.id, chunk.id);
                var panels = workflow.panels(novel.id);
                String base = "/novels/" + novel.id;
                mvc.perform(get(base + "/studio").session(session)).andExpect(status().isOk())
                                .andExpect(content().string(containsString("0 seleccionados")))
                                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("1 o 2 paneles por imagen"))));
                mvc.perform(form(base + "/studio").param("panelIds", panels.stream().map(panel -> panel.id.toString()).toArray(String[]::new)))
                                .andExpect(status().is3xxRedirection());
                var draft = workflow.drafts(novel.id).getFirst();
                assertThat(draft.prompt).contains("exactamente 3 viñetas", "Viñeta 3:", chunk.content);
        }

        @Test void modelsHaveIndependentFileConfiguration() {
                assertThat(environment.getProperty("app.text.model")).isEqualTo("meta/llama-3.1-8b-instruct");
                assertThat(environment.containsProperty("app.image.model")).isTrue();
                assertThat(environment.getProperty("app.image.model")).isEmpty();
                assertThat("test-key".equals(environment.getProperty("app.nvidia.api-key"))).isTrue();
        }

    @Test void studioProviderFailureKeepsTheSelectionAndDirection() throws Exception {
        var novel = workflow.createNovel("Escena fallida", "Lluvia en el aula.");
        workflow.proposeChunks(novel.id, 5000, "");
        workflow.confirmChunks(novel.id);
        var chunk = workflow.chunks(novel.id).getFirst();
        workflow.proposePanels(novel.id, chunk.id, 2, "");
        workflow.confirmPanels(novel.id, chunk.id);
        var panels = workflow.panels(novel.id);
        var character = workflow.addCharacter(novel.id, "Yuna", "Uniforme escolar.");
        doThrow(new local.higanbana.domain.ai.TextProviderException("NVIDIA esta temporalmente sobrecargado."))
                .when(text).studioPrompt(anyString());
        String base = "/novels/" + novel.id;
        var result = mvc.perform(form(base + "/studio")
                        .param("panelIds", panels.getFirst().id.toString(), panels.get(1).id.toString())
                        .param("characterIds", character.id.toString()).param("direction", "Luz suave"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getFlashMap().get("selectedPanelIds"))
                .isEqualTo(List.of(panels.getFirst().id, panels.get(1).id));
        assertThat(result.getFlashMap().get("selectedCharacterIds")).isEqualTo(List.of(character.id));
        var page = mvc.perform(get(base + "/studio").session(session).flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("NVIDIA esta temporalmente sobrecargado.")))
                .andExpect(content().string(containsString("Luz suave")))
                .andReturn().getResponse().getContentAsString();
        assertThat(java.util.regex.Pattern.compile("checked=\"checked\"").matcher(page).results().count()).isEqualTo(3);
        assertThat(workflow.drafts(novel.id)).isEmpty();
    }

    @Test void deletingStudioDescriptionPreservesPendingImageAndRequiresOwnershipAndCsrf() throws Exception {
        var novel = workflow.createNovel("Imagen", "La lluvia golpea la ventana.");
        var other = workflow.createNovel("Otra", "Otro texto.");
        workflow.proposeChunks(novel.id, 5000, "");
        workflow.confirmChunks(novel.id);
        var chunk = workflow.chunks(novel.id).getFirst();
        workflow.proposePanels(novel.id, chunk.id, 3, "");
        workflow.confirmPanels(novel.id, chunk.id);
        var panels = workflow.panels(novel.id);
        var draft = workflow.createDraft(novel.id, panels.stream().map(panel -> panel.id).toList(), "");
        var candidate = workflow.uploadImage(novel.id, draft.id, image().getBytes());
        String path = "/novels/" + novel.id + "/studio/" + draft.id + "/delete";
        mvc.perform(get("/novels/" + novel.id + "/studio").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Eliminar descripcion")));
        mvc.perform(post(path).session(session)).andExpect(status().isForbidden());
        mvc.perform(form("/novels/" + other.id + "/studio/" + draft.id + "/delete"))
                .andExpect(status().isBadRequest());
        assertThat(workflow.drafts(novel.id)).hasSize(1);
        mvc.perform(form(path)).andExpect(status().is3xxRedirection());
        assertThat(workflow.drafts(novel.id)).isEmpty();
        assertThat(workflow.candidate(novel.id, candidate.id).prompt).isEqualTo(candidate.prompt);
        mvc.perform(get("/novels/" + novel.id + "/candidates/" + candidate.id + "/image").session(session))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(form("/novels/" + novel.id + "/candidates/" + candidate.id + "/approve"))
                .andExpect(status().is3xxRedirection());
        var duplicate = workflow.createDraft(novel.id, List.of(panels.get(2).id), "");
        var duplicateCandidate = workflow.uploadImage(novel.id, duplicate.id, image().getBytes());
        mvc.perform(form("/novels/" + novel.id + "/candidates/" + duplicateCandidate.id + "/approve"))
                .andExpect(status().isBadRequest());
    }

        @Test void panelPageShowsUnavailableInsteadOfOfferingSimulatedImagePrompts() throws Exception {
                var novel = workflow.createNovel("Sin proveedor", "Aiko entra en el faro.");
                workflow.proposeChunks(novel.id, 5000, "");
                workflow.confirmChunks(novel.id);
                doReturn(false).when(text).imagePanelPromptsAvailable();

                mvc.perform(get("/novels/" + novel.id + "/panels").session(session))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("generacion de prompts de imagen no esta disponible")))
                                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Generar prompts de imagen"))));
        }

        @Test void allScreensAndCompleteHttpWorkflowWithCharacters() throws Exception {
        mvc.perform(form("/novels").param("title", "El ultimo tren").param("source", "La viajera abre su cuaderno.\n".repeat(30)))
                .andExpect(status().is3xxRedirection());
        long novelId = workflow.novels().getFirst().id;
        String base = "/novels/" + novelId;
        mvc.perform(get(base + "/characters").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavia no hay personajes")));
        mvc.perform(form(base + "/characters").param("name", "Aiko").param("description", "Ojos grises, abrigo rojo."))
                .andExpect(status().is3xxRedirection());
        long characterId = workflow.characters(novelId).getFirst().id;
        mvc.perform(form(base + "/characters/" + characterId + "/save")
                .param("name", "Aiko").param("description", "Ojos grises, abrigo blanco."))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/characters").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ojos grises, abrigo blanco.")));
        mvc.perform(get(base + "/chunks").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Criterio narrativo")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"limit\""))));
        mvc.perform(form(base + "/chunks/propose").param("revision", "Pausas"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/chunks").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("texto integro")));
        mvc.perform(form(base + "/chunks/confirm")).andExpect(status().is3xxRedirection());
        long chunkId = workflow.chunks(novelId).getFirst().id;
        String chunk = base + "/chunks/" + chunkId;
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk());
        mvc.perform(form(chunk + "/panels/propose").param("count", "1")).andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Prompts para generar imagen")))
                .andExpect(content().string(containsString("Prompt visual de prueba 1")))
                .andExpect(content().string(containsString("Regenerar prompts")))
                .andExpect(content().string(containsString("Regenerar reemplazara la propuesta")));
        mvc.perform(form(chunk + "/panels/save").param("imagePrompts", "Una viajera, tinta negra, primer plano."))
                .andExpect(status().is3xxRedirection());
        mvc.perform(form(chunk + "/panels/confirm")).andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk());
        mvc.perform(get(base + "/studio").session(session)).andExpect(status().isOk());
        long panelId = workflow.panels(novelId).getFirst().id;
        mvc.perform(form(base + "/studio").param("panelIds", Long.toString(panelId)).param("direction", "Luz lateral")
                .param("characterIds", Long.toString(characterId)))
                .andExpect(status().is3xxRedirection());
        long draftId = workflow.drafts(novelId).getFirst().id;
        mvc.perform(get(base + "/studio").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Prompt final editable")))
                .andExpect(content().string(containsString("Copiar prompt completo")))
                .andExpect(content().string(containsString("Subir imagen para revision")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Imagen DEMO"))));
        mvc.perform(form(base + "/studio/" + draftId + "/save").param("prompt", "La viajera en el anden, amanecer."))
                .andExpect(status().is3xxRedirection());
        var invalid = new MockMultipartFile("image", "fake.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart(base + "/studio/" + draftId + "/upload").file(invalid).session(session)
                .param("_csrf", session.getAttribute("csrf").toString()).param("prompt", "No debe guardarse."))
                .andExpect(status().isBadRequest());
        assertThat(workflow.drafts(novelId).getFirst().prompt).isEqualTo("La viajera en el anden, amanecer.");
        assertThat(workflow.candidates(novelId)).isEmpty();
        mvc.perform(multipart(base + "/studio/" + draftId + "/upload").file(image()).session(session)
                .param("prompt", "Sin token."))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(base + "/studio/" + draftId + "/upload").file(image()).session(session)
                .param("_csrf", session.getAttribute("csrf").toString()).param("prompt", "La viajera en el anden, amanecer."))
                .andExpect(status().is3xxRedirection());
        long candidateId = workflow.candidates(novelId).getFirst().id;
        assertThat(workflow.candidate(novelId, candidateId).prompt).contains("Aiko", "Ojos grises, abrigo blanco.");
        mvc.perform(form(base + "/characters/" + characterId + "/delete")).andExpect(status().is3xxRedirection());
        assertThat(workflow.characters(novelId)).isEmpty();
        assertThat(workflow.candidate(novelId, candidateId).prompt).contains("Ojos grises, abrigo blanco.");
        mvc.perform(get(base + "/generation").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Pendiente")));
        mvc.perform(get(base + "/candidates/" + candidateId + "/image").session(session))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get(base + "/reader").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavia no hay imagenes aprobadas")));
        try (var files = Files.list(storage)) { assertThat(files.count()).isZero(); }
        mvc.perform(form(base + "/candidates/" + candidateId + "/approve")).andExpect(status().is3xxRedirection());
        mvc.perform(form(base + "/candidates/" + candidateId + "/approve")).andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/generation").session(session)).andExpect(status().isOk());
        mvc.perform(get(base + "/reader").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Secuencia 1")));
        var entry = workflow.reader(novelId).getFirst();
        mvc.perform(get(base + "/entries/" + entry.id + "/image").session(session)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        assertThat(workflow.reader(novelId)).hasSize(1);
        try (var files = Files.list(storage)) { assertThat(files.count()).isEqualTo(1); }
        mvc.perform(form(base + "/chunks/propose").param("limit", "500"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("ya estan confirmados")));
        assertThat(workflow.panels(novelId)).hasSize(1);
        mvc.perform(multipart(base + "/studio/" + draftId + "/upload").file(image()).session(session)
                .param("_csrf", session.getAttribute("csrf").toString()).param("prompt", "La viajera en el anden, amanecer."))
                .andExpect(status().is3xxRedirection());
        long rejected = workflow.candidates(novelId).getFirst().id;
        mvc.perform(form(base + "/candidates/" + rejected + "/reject")).andExpect(status().is3xxRedirection());
        mvc.perform(form(base + "/candidates/" + rejected + "/approve")).andExpect(status().isBadRequest());
        mvc.perform(get(base + "/generation").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Rechazado")));
    }

        @Test void aiResponseCacheTableAndJdbcRepositoryWorkInH2Tests() {
                String key = "c".repeat(64);
                aiResponseCache.store(key, "describe-v1", "https://provider.test", "test-model", "prompt-v1",
                        "Respuesta de prueba.", java.time.Duration.ofDays(1));
                assertThat(aiResponseCache.find(key)).contains("Respuesta de prueba.");
        }

    @Test void rejectsMissingCsrfAndInvalidUtf8() throws Exception {
        mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + session.getAttribute("csrf") + "\"")));
        mvc.perform(post("/novels").session(session).param("title", "No token").param("source", "Text"))
                .andExpect(status().isForbidden());
        var file = new MockMultipartFile("file", "bad.txt", "text/plain", new byte[]{(byte) 0xC3, 0x28});
        mvc.perform(multipart("/novels").file(file).session(session).param("title", "Invalid")
                .param("_csrf", session.getAttribute("csrf").toString()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("UTF-8 valido")));
        var oversized = new MockMultipartFile("file", "large.txt", "text/plain", new byte[2_000_001]);
        mvc.perform(multipart("/novels").file(oversized).session(session).param("title", "Large")
                .param("_csrf", session.getAttribute("csrf").toString()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("limite de 2 MB")));
    }

    @Test void characterFormsValidateOwnershipCsrfAndEscapeDescriptions() throws Exception {
        var novel = workflow.createNovel("Reparto web", "Texto original.");
        var other = workflow.createNovel("Otra novela", "Otro texto.");
        String base = "/novels/" + novel.id;
        mvc.perform(form(base + "/characters").param("name", " ").param("description", "Descripcion"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/characters").session(session).param("name", "Aiko").param("description", "Descripcion"))
                .andExpect(status().isForbidden());
        mvc.perform(form(base + "/characters").param("name", "Aiko").param("description", "<script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection());
        long characterId = workflow.characters(novel.id).getFirst().id;
        mvc.perform(get(base + "/characters").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
        mvc.perform(form("/novels/" + other.id + "/characters/" + characterId + "/save")
                .param("name", "Ajeno").param("description", "Cambio"))
                .andExpect(status().isBadRequest());
        mvc.perform(form("/novels/" + other.id + "/characters/" + characterId + "/delete"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/characters/" + characterId + "/delete").session(session))
                .andExpect(status().isForbidden());
        assertThat(workflow.characters(novel.id)).hasSize(1);
    }

    @Test void acceptsUtf8FileAndBlocksCrossNovelHttpRequests() throws Exception {
        var file = new MockMultipartFile("file", "novel.txt", "text/plain", "Inicio\r\n\u00c1rbol y luz. ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/novels").file(file).session(session).param("title", "Archivo")
                .param("_csrf", session.getAttribute("csrf").toString())).andExpect(status().is3xxRedirection());
        var original = workflow.novels().getFirst();
        assertThat(original.source).isEqualTo("Inicio\r\n\u00c1rbol y luz. ");
        var foreign = workflow.createNovel("Otra", "Texto ajeno");
        workflow.proposeChunks(foreign.id, 200, "");
        workflow.confirmChunks(foreign.id);
        long foreignChunk = workflow.chunks(foreign.id).getFirst().id;
        mvc.perform(form("/novels/" + original.id + "/chunks/" + foreignChunk + "/panels/propose").param("count", "2"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("no pertenece")));
    }
}