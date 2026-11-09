package local.higanbana;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.nvidia.api-key=test-key", "app.text.model=meta/llama-3.1-8b-instruct", "app.image.model="})
@AutoConfigureMockMvc
class WebFlowTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.manga-dir", () -> storage.toString());
        registry.add("app.text-mode", () -> "demo");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:webflow;DB_CLOSE_DELAY=-1");
    }
    @Autowired MockMvc mvc;
    @Autowired Workflow workflow;
    @Autowired org.springframework.core.env.Environment environment;
    MockHttpSession session;

    @BeforeEach void session() throws Exception {
        session = (MockHttpSession) mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nueva obra"))).andReturn().getRequest().getSession();
    }

    private MockHttpServletRequestBuilder form(String path) {
        return post(path).session(session).param("_csrf", session.getAttribute("csrf").toString());
    }

        @Test void modelsHaveIndependentFileConfiguration() {
                assertThat(environment.getProperty("app.text.model")).isEqualTo("meta/llama-3.1-8b-instruct");
                assertThat(environment.containsProperty("app.image.model")).isTrue();
                assertThat(environment.getProperty("app.image.model")).isEmpty();
                assertThat("test-key".equals(environment.getProperty("app.nvidia.api-key"))).isTrue();
        }

    @Test void allSixScreensAndCompleteHttpWorkflow() throws Exception {
        mvc.perform(form("/novels").param("title", "El ultimo tren").param("source", "La viajera abre su cuaderno.\n".repeat(30)))
                .andExpect(status().is3xxRedirection());
        long novelId = workflow.novels().getFirst().id;
        String base = "/novels/" + novelId;
        mvc.perform(get(base + "/chunks").session(session)).andExpect(status().isOk());
        mvc.perform(form(base + "/chunks/propose").param("limit", "400").param("revision", "Pausas"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/chunks").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("texto integro")));
        mvc.perform(form(base + "/chunks/confirm")).andExpect(status().is3xxRedirection());
        long chunkId = workflow.chunks(novelId).getFirst().id;
        String chunk = base + "/chunks/" + chunkId;
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk());
        mvc.perform(form(chunk + "/panels/propose").param("count", "1")).andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Propuesta de secuencia")));
        mvc.perform(form(chunk + "/panels/save").param("descriptions", "Una viajera, tinta negra, primer plano."))
                .andExpect(status().is3xxRedirection());
        mvc.perform(form(chunk + "/panels/confirm")).andExpect(status().is3xxRedirection());
        mvc.perform(get(base + "/panels").session(session)).andExpect(status().isOk());
        mvc.perform(get(base + "/studio").session(session)).andExpect(status().isOk());
        long panelId = workflow.panels(novelId).getFirst().id;
        mvc.perform(form(base + "/studio").param("panelIds", Long.toString(panelId)).param("direction", "Luz lateral"))
                .andExpect(status().is3xxRedirection());
        long draftId = workflow.drafts(novelId).getFirst().id;
        mvc.perform(get(base + "/studio").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Descripcion editable")));
        mvc.perform(form(base + "/studio/" + draftId + "/save").param("prompt", "La viajera en el anden, amanecer."))
                .andExpect(status().is3xxRedirection());
        mvc.perform(form(base + "/studio/" + draftId + "/generate")).andExpect(status().is3xxRedirection());
        long candidateId = workflow.candidates(novelId).getFirst().id;
        mvc.perform(get(base + "/generation").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Pendiente")));
        mvc.perform(get(base + "/candidates/" + candidateId + "/image").session(session))
                .andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().string(containsString("<svg")));
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
                .andExpect(content().string(containsString("DEMO")));
        assertThat(workflow.reader(novelId)).hasSize(1);
        try (var files = Files.list(storage)) { assertThat(files.count()).isEqualTo(1); }
        mvc.perform(form(base + "/chunks/propose").param("limit", "500"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("ya estan confirmados")));
        assertThat(workflow.panels(novelId)).hasSize(1);
        mvc.perform(form(base + "/studio/" + draftId + "/generate")).andExpect(status().is3xxRedirection());
        long rejected = workflow.candidates(novelId).getFirst().id;
        mvc.perform(form(base + "/candidates/" + rejected + "/reject")).andExpect(status().is3xxRedirection());
        mvc.perform(form(base + "/candidates/" + rejected + "/approve")).andExpect(status().isBadRequest());
        mvc.perform(get(base + "/generation").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Rechazado")));
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