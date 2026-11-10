package local.higanbana;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import local.higanbana.domain.Chunking;
import local.higanbana.infrastructure.ai.TextAssistant;
import java.util.Map;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TextAssistantTest {
        private TextAssistant assistant(String mode, String baseUrl, String key, String model,
                                                                   ObjectMapper mapper, RestClient.Builder builder) {
                return assistant(mode, baseUrl, key, model, mapper, builder, new InMemoryAiResponseCache());
        }

        private TextAssistant assistant(String mode, String baseUrl, String key, String model,
                                                                   ObjectMapper mapper, RestClient.Builder builder, InMemoryAiResponseCache cache) {
                return new TextAssistant(mode, baseUrl, key, model, mapper, builder, cache, Duration.ofDays(30));
        }

        @Test void nvidiaRequiresTextModelAndDoesNotAcceptTextAsMode() {
                assertThatIllegalArgumentException().isThrownBy(() -> assistant("nvidia",
                                "https://integrate.api.nvidia.com", "test-key", "", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("modelo de texto");
                assertThatIllegalArgumentException().isThrownBy(() -> assistant("text",
                                "https://integrate.api.nvidia.com", "test-key", "test-model", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("app.text-mode debe ser demo o nvidia");
        }

        @Test void nvidiaExtractsImagePromptsWithoutAWrapperRecord() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("prompts independientes")))
                .andRespond(withSuccess(completion("{\"panels\":[\"Plano general.\",\"Primer plano.\"]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.imagePanelPrompts("Una estacion.", 2, ""))
                .containsExactly("Plano general.", "Primer plano.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"panels\":null}", "{\"panels\":\"Plano\"}", "{\"panels\":{}}"})
    void nvidiaRejectsMissingNullOrNonArrayPanels(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("Una estacion.", 1, ""))
                .withMessageContaining("prompts de imagen no validos");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"ends\":null}", "{\"ends\":1}", "{\"ends\":{}}", "{\"ends\":[]}"})
    void nvidiaRejectsMissingNullOrInvalidChunkEnds(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("Una estacion.", 200, ""))
                .withMessageContaining("escenas no validas");
        server.verify();
    }

    private String completion(String payload) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "test", "object", "chat.completion", "created", 1, "model", "test-model",
                "choices", java.util.List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", payload)))));
    }

        @Test void narrativeScenesContinueAcrossTechnicalWindowsWithoutRewritingSource() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("a".repeat(300))))
                .andRespond(withSuccess(completion("{\"ends\":[1],\"continuesPrevious\":false}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("b".repeat(300))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ESCENA ANTERIOR")))
                .andRespond(withSuccess(completion("{\"ends\":[1],\"continuesPrevious\":true}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        String source = "a".repeat(900) + "b".repeat(900);
        var chunks = assistant.chunks(source, 900, "Escenas breves");
        assertThat(chunks).containsExactly(source);
        assertThat(String.join("", chunks)).isEqualTo(source);
        server.verify();
    }

    @Test void invalidNvidiaCutsAreRejectedInsteadOfDroppingSource() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"id":"chunks","object":"chat.completion","created":1,"model":"test-model",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"ends\\\":[0,3]}"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("a".repeat(900), 900, ""))
                .withMessageContaining("escenas no validas");
        server.verify();
    }

    @Test void narrativeCutsFollowSentencesRatherThanCharacterQuotas() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"ends\":[2,3],\"continuesPrevious\":false}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        String firstScene = "Aiko espera. Rin llega. ";
        String secondScene = "Al dia siguiente viajan al bosque.";
        assertThat(assistant.chunks(firstScene + secondScene, 4000, "Separar los cambios de tiempo"))
                .containsExactly(firstScene, secondScene);
        server.verify();
    }

    @Test void narrativeChunkingHasNoDemoFallback() {
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "", "test-model",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.chunkingAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("Una escena.", 4000, ""))
                .withMessageContaining("requiere configurar NVIDIA");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ends\":[1],\"continuesPrevious\":true}",
            "{\"ends\":[1],\"continuesPrevious\":\"false\"}",
            "{\"ends\":[1.5],\"continuesPrevious\":false}",
            "{\"ends\":[\"1\"],\"continuesPrevious\":false}",
            "{\"ends\":[1,1],\"continuesPrevious\":false}",
            "{\"ends\":[0],\"continuesPrevious\":false}"
    })
    void invalidNarrativeContinuityOrIndicesNeverProduceChunks(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("Una escena.", 4000, ""))
                .withMessageContaining("escenas no validas");
        server.verify();
    }

    @Test void brokenSecondWindowDoesNotReturnAPartialNovel() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"ends\":[1],\"continuesPrevious\":false}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"ends\":[],\"continuesPrevious\":true}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("a".repeat(1800), 900, ""))
                .withMessageContaining("no se ha guardado una division parcial");
        server.verify();
    }

        @Test void nvidiaUsesOpenAiContractToGenerateImagePromptsWithBoundedOutput() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("meta/llama-3.1-8b-instruct"))
                .andExpect(jsonPath("$.max_tokens").value(1800))
                .andRespond(withSuccess("""
                        {"id":"test","object":"chat.completion","created":1,"model":"meta/llama-3.1-8b-instruct",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"panels\\\":[\\\"Una viajera en primer plano, lluvia al fondo.\\\"]}"},"finish_reason":"stop"}],
                        "usage":{"prompt_tokens":10,"completion_tokens":8,"total_tokens":18}}
                        """, MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "meta/llama-3.1-8b-instruct", new ObjectMapper(), builder);
        assertThat(assistant.imagePanelPrompts("Una estacion.", 1, "Luz lateral"))
                .containsExactly("Una viajera en primer plano, lluvia al fondo.");
        assertThat(assistant.provenance()).contains("NVIDIA");
        server.verify();
    }

    @Test void identicalRequestsReuseSuccessfulResponseWithoutCallingNvidiaAgain() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Retrato en tinta negra.\"]}"), MediaType.APPLICATION_JSON));
        var cache = new InMemoryAiResponseCache();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder, cache);

        assertThat(assistant.imagePanelPrompts("Aiko bajo la lluvia.", 1, "Plano cercano."))
                .containsExactly("Retrato en tinta negra.");
        assertThat(assistant.imagePanelPrompts("Aiko bajo la lluvia.", 1, "Plano cercano."))
                .containsExactly("Retrato en tinta negra.");
        server.verify();
    }

    @Test void repeatedNarrativeWindowUsesCachedSceneBoundaries() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"ends\":[2],\"continuesPrevious\":false}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder, new InMemoryAiResponseCache());
        String source = "Aiko espera. Rin llega.";

        assertThat(assistant.chunks(source, 4000, "Agrupar la escena"))
                .containsExactly("Aiko espera. Rin llega.");
        assertThat(assistant.chunks(source, 4000, "Agrupar la escena"))
                .containsExactly("Aiko espera. Rin llega.");
        server.verify();
    }

    @Test void cacheKeyChangesWhenModelOrCompletePromptChanges() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("model-a"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Respuesta A.\"]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("model-b"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Respuesta B.\"]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.model").value("model-a"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Respuesta C.\"]}"), MediaType.APPLICATION_JSON));
        var cache = new InMemoryAiResponseCache();

        assertThat(assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "model-a",
                new ObjectMapper(), builder, cache).imagePanelPrompts("Aiko espera.", 1, "Plano general."))
                .containsExactly("Respuesta A.");
        assertThat(assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "model-b",
                new ObjectMapper(), builder, cache).imagePanelPrompts("Aiko espera.", 1, "Plano general."))
                .containsExactly("Respuesta B.");
        assertThat(assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "model-a",
                new ObjectMapper(), builder, cache).imagePanelPrompts("Aiko corre.", 1, "Plano general."))
                .containsExactly("Respuesta C.");
        server.verify();
    }

    @Test void malformedProviderResponseIsNeverCached() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Uno\"]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Uno\"]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder, new InMemoryAiResponseCache());

        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("Aiko espera.", 2, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("Aiko espera.", 2, ""));
        server.verify();
    }

    @Test void promptBuilderOmitsBlankRevisionAndAddsProvidedRevision() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "<source>Una estacion.</source>")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Revision del usuario:"))))
                .andRespond(withSuccess(completion("{\"panels\":[\"Respuesta\"]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Revision del usuario: Separa el salto temporal.")))
                .andRespond(withSuccess(completion("{\"panels\":[\"Respuesta\"]}"), MediaType.APPLICATION_JSON));

        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.imagePanelPrompts("Una estacion.", 1, "   ")).containsExactly("Respuesta");
        assertThat(assistant.imagePanelPrompts("Una estacion.", 1, "Separa el salto temporal."))
                .containsExactly("Respuesta");
        server.verify();
    }

    @Test void emptyNvidiaTextIsRejectedByOptionalResponseExtraction() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(""), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);

                assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("Una estacion.", 1, ""))
                        .withMessageContaining("Palo No value present");
        server.verify();
    }

    @Test void rejectsOversizeContextWithoutSendingRequest() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("a".repeat(8001), 1, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("context", 1, "a".repeat(1501)));
        server.verify();
    }

    @Test void providerFailureNeverFallsBackToDemoOrRevealsKey() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "private-test-key",
                "test-model", new ObjectMapper(), builder);
                assertThatThrownBy(() -> assistant.imagePanelPrompts("context", 1, ""))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Palo 401")
                        .hasMessageNotContaining("private-test-key");
        server.verify();
    }

    @Test void imagePanelPromptsAreUnavailableWithoutNvidiaAndNeverUseDemoText() {
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "", "unused",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.provenance()).contains("DEMO", "sin IA");
        assertThat(assistant.imagePanelPromptsAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts("Fin.", 1, ""))
                .withMessageContaining("requiere NVIDIA");
    }
}