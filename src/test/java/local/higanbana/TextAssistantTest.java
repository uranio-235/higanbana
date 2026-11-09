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
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TextAssistantTest {
        @Test void nvidiaRequiresTextModelAndDoesNotAcceptTextAsMode() {
                assertThatIllegalArgumentException().isThrownBy(() -> new TextAssistant("nvidia",
                                "https://integrate.api.nvidia.com", "test-key", "", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("modelo de texto");
                assertThatIllegalArgumentException().isThrownBy(() -> new TextAssistant("text",
                                "https://integrate.api.nvidia.com", "test-key", "test-model", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("app.text-mode debe ser demo o nvidia");
        }

    @Test void nvidiaExtractsPanelsWithoutAWrapperRecord() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Plano general.\",\"Primer plano.\"]}"), MediaType.APPLICATION_JSON));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.panels("Una estacion.", 2, "")).containsExactly("Plano general.", "Primer plano.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"panels\":null}", "{\"panels\":\"Plano\"}", "{\"panels\":{}}"})
    void nvidiaRejectsMissingNullOrNonArrayPanels(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.panels("Una estacion.", 1, ""))
                .withMessageContaining("propuesta no valida");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"ends\":null}", "{\"ends\":1}", "{\"ends\":{}}", "{\"ends\":[]}"})
    void nvidiaRejectsMissingNullOrInvalidChunkEnds(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("Una estacion.", 200, ""))
                .withMessageContaining("cortes de chunks no validos");
        server.verify();
    }

    private String completion(String payload) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "test", "object", "chat.completion", "created", 1, "model", "test-model",
                "choices", java.util.List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", payload)))));
    }

    @Test void nvidiaSegmentsEveryWindowWithoutRewritingSource() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String completion = """
                {"id":"chunks","object":"chat.completion","created":1,"model":"test-model",
                "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"ends\\\":[1,3]}"},"finish_reason":"stop"}]}
                """;
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("a".repeat(300))))
                .andRespond(withSuccess(completion, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("b".repeat(300))))
                .andRespond(withSuccess(completion, MediaType.APPLICATION_JSON));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        String source = "a".repeat(900) + "b".repeat(900);
        var chunks = assistant.chunks(source, 900, "Escenas breves");
        assertThat(chunks).hasSize(4).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(900));
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
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks("a".repeat(900), 900, ""))
                .withMessageContaining("cortes de chunks no validos");
        server.verify();
    }

    @Test void nvidiaUsesOpenAiContractWithBoundedOutput() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("meta/llama-3.1-8b-instruct"))
                .andExpect(jsonPath("$.max_tokens").value(1800))
                .andRespond(withSuccess("""
                        {"id":"test","object":"chat.completion","created":1,"model":"meta/llama-3.1-8b-instruct",
                        "choices":[{"index":0,"message":{"role":"assistant","content":"Un plano general."},"finish_reason":"stop"}],
                        "usage":{"prompt_tokens":10,"completion_tokens":8,"total_tokens":18}}
                        """, MediaType.APPLICATION_JSON));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "meta/llama-3.1-8b-instruct", new ObjectMapper(), builder);
        assertThat(assistant.describe("Una estacion.", "Luz lateral")).isEqualTo("Un plano general.");
        assertThat(assistant.provenance()).contains("NVIDIA");
        server.verify();
    }

    @Test void rejectsOversizeContextWithoutSendingRequest() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.describe("a".repeat(8001), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.describe("context", "a".repeat(1501)));
        server.verify();
    }

    @Test void providerFailureNeverFallsBackToDemoOrRevealsKey() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var assistant = new TextAssistant("nvidia", "https://integrate.api.nvidia.com", "private-test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatThrownBy(() -> assistant.describe("context", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No se pudo obtener texto")
                .hasMessageNotContaining("private-test-key");
        server.verify();
    }

    @Test void demoIsExplicitAndProducesRequestedPanelCountEvenForLongChunks() {
        var assistant = new TextAssistant("demo", "https://integrate.api.nvidia.com", "", "unused",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.provenance()).contains("DEMO", "sin IA");
        assertThat(assistant.panels("Texto narrativo. ".repeat(450), 1, "")).hasSize(1)
                .allSatisfy(panel -> assertThat(panel.length()).isLessThan(3000));
        assertThat(assistant.panels("Fin.", 12, "")).hasSize(12);
    }
}