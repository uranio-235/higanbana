package local.higanbana;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import local.higanbana.domain.ai.ChunkRequestBuilder;
import local.higanbana.domain.ai.PanelPromptRequestBuilder;
import local.higanbana.domain.ai.StudioPromptRequest;
import local.higanbana.domain.ai.StudioPromptRequestBuilder;
import local.higanbana.domain.ai.PromptCharacterBuilder;
import local.higanbana.application.MemoryService;
import local.higanbana.domain.memory.TextEmbedder;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.infrastructure.memory.InMemoryMemoryRepository;
import java.util.Map;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TextAssistantTest {
        private PanelPromptRequestBuilder panels(String source) {
                return new PanelPromptRequestBuilder()
                        .source(source)
                        .count(1)
                        .direction("");
        }

        private ChunkRequestBuilder chunks(String source) {
                return new ChunkRequestBuilder()
                        .source(source)
                        .limit(4000)
                        .revision("");
        }

        private StudioPromptRequest studio(String source) {
                return new StudioPromptRequestBuilder()
                        .source(source)
                        .panelPrompts(java.util.List.of("Una viñeta."))
                        .direction("")
                        .characters(java.util.List.of())
                        .build();
        }

        private String panelDescription(String prompt) {
                assertThat(prompt).startsWith("Crea una imagen de manga japones en blanco y negro");
                return prompt.substring(prompt.indexOf("\n\n") + 2);
        }

        @Test void coverPromptUsesSelectedFragmentsAndRejectsMissingPrompt() throws Exception {
            RestClient.Builder builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("La lluvia")))
                    .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("El faro")))
                    .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Reserva espacio para el titulo")))
                    .andRespond(withSuccess(completion("{\"prompt\":\"Un faro bajo la lluvia.\"}"), MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andRespond(withSuccess(completion("{\"prompt\":null}"), MediaType.APPLICATION_JSON));
            var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                    "test-model", new ObjectMapper(), builder);
            String prompt = assistant.coverPrompt("La flor que crecía bajo la lluvia", "La lluvia\nEl faro");
            assertThat(prompt).contains("\"La flor que crecía bajo la lluvia\"", "texto visible y legible",
                    "no lo traduzcas ni lo cambies", "Un faro bajo la lluvia.");
            assertThatIllegalArgumentException().isThrownBy(() -> assistant.coverPrompt("Otra", "Texto"))
                    .withMessageContaining("portada no valido");
            server.verify();
        }

        @Test void coverPromptReservesSpaceForExactTitleAndRejectsOversizeResponse() throws Exception {
            String title = "¡La flor: «bajo la lluvia»!";
            String prefix = new StringBuilder("Incluye en la portada el siguiente titulo exacto como texto visible y legible, ")
                    .append("con tipografia integrada en la composicion. Conserva todas sus letras, tildes y signos; ")
                    .append("no lo traduzcas ni lo cambies. No dibujes las comillas exteriores que delimitan el titulo:\n\"")
                    .append(title).append("\"\n\n").toString();
            int limit = 4000 - prefix.length();
            RestClient.Builder builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andRespond(withSuccess(completion(new ObjectMapper().writeValueAsString(Map.of("prompt", "x".repeat(limit)))), MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andRespond(withSuccess(completion(new ObjectMapper().writeValueAsString(Map.of("prompt", "x".repeat(limit + 1)))), MediaType.APPLICATION_JSON));
            var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                    "test-model", new ObjectMapper(), builder);
            assertThat(assistant.coverPrompt(title, "Primera escena")).hasSize(4000).startsWith(prefix);
            assertThatIllegalArgumentException().isThrownBy(() -> assistant.coverPrompt(title, "Otra escena"))
                    .withMessageContaining("portada no valido");
            server.verify();
        }

        private TextAssistant assistant(String mode, String baseUrl, String key, String model,
                                                                   ObjectMapper mapper, RestClient.Builder builder) {
                return assistant(mode, baseUrl, key, model, mapper, builder, new InMemoryAiResponseCache());
        }

        private TextAssistant assistant(String mode, String baseUrl, String key, String model,
                                                                   ObjectMapper mapper, RestClient.Builder builder, InMemoryAiResponseCache cache) {
                TextEmbedder embedder = text -> {
                        float[] vector = new float[MemoryService.EMBEDDING_DIMENSIONS];
                        vector[Math.floorMod(text.hashCode(), vector.length)] = 1;
                        return vector;
                };
                return new TextAssistant(mode, baseUrl, key, model, mapper, builder, cache,
                        new MemoryService(embedder, new InMemoryMemoryRepository()), Duration.ofDays(30));
        }

        @Test void nvidiaRequiresTextModelAndDoesNotAcceptTextAsMode() {
                assertThatIllegalArgumentException().isThrownBy(() -> assistant("nvidia",
                                "https://integrate.api.nvidia.com", "test-key", "", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("modelo de texto");
                assertThatIllegalArgumentException().isThrownBy(() -> assistant("text",
                                "https://integrate.api.nvidia.com", "test-key", "test-model", new ObjectMapper(), RestClient.builder()))
                                .withMessageContaining("app.text-mode debe ser demo o nvidia");
        }

    @Test void characterExtractionSendsTheWholeSelectedChunkOnceAndReturnsPrompts() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String source = "Aiko y Rin llegan." + " ".repeat(5000) + "Sora se marcha.";
        var existing = java.util.List.of(new PromptCharacterBuilder().name("Aiko").description("Cabello oscuro.").build());
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Aiko y Rin llegan.")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Sora se marcha.")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Cabello oscuro.")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("ficha de identidad visual reutilizable")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("conserva solo 'cabello oscuro'")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("prompt completo fusionado")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("La plausibilidad no es evidencia")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Omite lo desconocido")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("aunque se nombre y describa su color")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Conserva los rasgos estables acumulados aunque este chunk no los repita")))
                .andRespond(withSuccess(completion("{\"characters\":[{\"name\":\"Aiko\",\"prompt\":\"Abrigo blanco.\"},{\"name\":\"Sora\",\"prompt\":\"Pelo negro.\"}]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.extractCharacters(source, existing)).extracting(character -> character.name())
                .containsExactly("Aiko", "Sora");
        assertThat(assistant.extractCharacters(source, existing)).extracting(character -> character.description())
                .containsExactly("Abrigo blanco.", "Pelo negro.");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"characters\":null}", "{\"characters\":[null]}", "{\"characters\":[\"\"]}",
            "{\"characters\":\"Aiko\"}", "{\"characters\":[123]}", "{\"characters\":[{\"name\":\"Aiko\",\"prompt\":null}]}"})
    void characterExtractionRejectsInvalidLists(String payload) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(payload), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.extractCharacters("Una novela.", java.util.List.of()))
                .withMessageContaining("personajes no validos");
        server.verify();
    }

    @Test void extractionCacheIncludesTheCurrentCharacterSheets() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"characters\":[{\"name\":\"Aiko\",\"prompt\":\"Cabello oscuro.\"}]}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Ojos grises.")))
                .andRespond(withSuccess(completion("{\"characters\":[{\"name\":\"Aiko\",\"prompt\":\"Cabello oscuro. Ojos grises.\"}]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        var first = assistant.extractCharacters("Aiko espera.", java.util.List.of());
        var updated = assistant.extractCharacters("Aiko espera.", java.util.List.of(
                new PromptCharacterBuilder().name("Aiko").description("Ojos grises.").build()));
        assertThat(first.getFirst().description()).isEqualTo("Cabello oscuro.");
        assertThat(updated.getFirst().description()).isEqualTo("Cabello oscuro. Ojos grises.");
        server.verify();
    }

    @Test void characterRevisionUsesExistingPromptAndInstructions() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("PROMPT ACTUAL")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Cambia el abrigo a oscuro")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("ficha de identidad visual reutilizable")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("llevarla en una escena")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Omite lo desconocido")))
                .andRespond(withSuccess(completion("{\"prompt\":\"Aiko, ojos grises y abrigo oscuro.\"}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.reviseCharacterPrompt("Aiko", "Aiko con abrigo claro.", "Cambia el abrigo a oscuro"))
                .isEqualTo("Aiko, ojos grises y abrigo oscuro.");
        server.verify();
    }

    @Test void characterGenerationRequiresRealProvider() {
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "", "test-model",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.characterPromptsAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.extractCharacters("Texto", java.util.List.of()))
                .withMessageContaining("requiere configurar NVIDIA");
    }

    @Test void characterGenerationRejectsInvalidFinalPrompt() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"prompt\":null}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.reviseCharacterPrompt("Aiko", "Abrigo.", "Oscuro"));
        server.verify();
    }

        @Test void nvidiaExtractsImagePromptsWithoutAWrapperRecord() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("prompts independientes")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("manga japones en blanco y negro")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("tramas de semitonos")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("globos de dialogo solo con intervenciones literales")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("cartelas de narrador con fragmentos descriptivos breves y literales")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("No inventes ni parafrasees texto")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("detalles visuales concretos del fragmento")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("No sustituyas esos detalles")))
                .andRespond(withSuccess(completion("{\"panels\":[\"Plano general.\",\"Primer plano.\"]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThat(assistant.imagePanelPrompts(panels("Una estacion.").count(2).build()))
                .map(this::panelDescription).containsExactly("Plano general.", "Primer plano.");
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
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("Una estacion.").build()))
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
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks(chunks("Una estacion.").limit(200).build()))
                .withMessageContaining("escenas no validas");
        server.verify();
    }

    private String completion(String payload) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "test", "object", "chat.completion", "created", 1, "model", "test-model",
                "choices", java.util.List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", payload)))));
    }

    private String characterToolCall(String tool, String arguments) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(Map.of(
                "id", "test", "object", "chat.completion", "created", 1, "model", "test-model",
                "choices", java.util.List.of(Map.of("index", 0, "finish_reason", "tool_calls",
                        "message", Map.of("role", "assistant", "tool_calls", java.util.List.of(Map.of(
                                "id", "call_characters", "type", "function",
                                "function", Map.of("name", tool, "arguments", arguments))))))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void promptGeneratorsRetrieveOnlyRequestedCharactersAndCacheTheResult(boolean studio) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var cast = java.util.List.of(
                new PromptCharacterBuilder().name("Aiko").description("Cabello corto, ojos grises.").build(),
                new PromptCharacterBuilder().name("Rin").description("Lunar en la mejilla.").build(),
                new PromptCharacterBuilder().name("Sora").description("Trenza larga.").build());
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.tools[0].function.name").value("lookup_characters"))
                .andExpect(jsonPath("$.parallel_tool_calls").value(false))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Aiko")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Sora")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Cabello corto"))))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Lunar en la mejilla"))))
                .andRespond(withSuccess(characterToolCall("lookup_characters", "{\"names\":[\"Aiko\",\"Rin\"]}"),
                        MediaType.APPLICATION_JSON));
        String finalJson = studio ? "{\"prompt\":\"Aiko de ojos grises junto a Rin con su lunar.\"}"
                : "{\"panels\":[\"Aiko de ojos grises junto a Rin con su lunar.\"]}";
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id").value("call_characters"))
                .andExpect(jsonPath("$.messages[2].role").value("tool"))
                .andExpect(jsonPath("$.messages[2].tool_call_id").value("call_characters"))
                .andExpect(jsonPath("$.messages[2].content", org.hamcrest.Matchers.containsString("Cabello corto, ojos grises.")))
                .andExpect(jsonPath("$.messages[2].content", org.hamcrest.Matchers.containsString("Lunar en la mejilla.")))
                .andExpect(jsonPath("$.messages[2].content", org.hamcrest.Matchers.containsString("similarity")))
                .andExpect(jsonPath("$.messages[2].content", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Trenza larga"))))
                .andRespond(withSuccess(completion(finalJson), MediaType.APPLICATION_JSON));
        var cache = new InMemoryAiResponseCache();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder, cache);
        if (studio) {
            var request = new StudioPromptRequestBuilder().from(studio("Aiko y Rin esperan."))
                    .availableCharacters(cast).build();
            var first = assistant.studioPrompt(request);
            assertThat(first.text()).contains("Aiko de ojos grises junto a Rin con su lunar.");
            assertThat(first.characters()).containsExactly(cast.get(0), cast.get(1));
            var anotherAssistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                    "test-model", new ObjectMapper(), builder, cache);
            assertThat(anotherAssistant.studioPrompt(request)).isEqualTo(first);
        } else {
            var request = panels("Aiko y Rin esperan.").characters(cast).build();
            assertThat(assistant.imagePanelPrompts(request)).map(this::panelDescription)
                    .containsExactly("Aiko de ojos grises junto a Rin con su lunar.");
            assertThat(assistant.imagePanelPrompts(request)).map(this::panelDescription)
                    .containsExactly("Aiko de ojos grises junto a Rin con su lunar.");
        }
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"names\":[]}", "{\"names\":\"Aiko\"}", "{\"names\":[1]}",
            "{\"names\":[\"Aiko\"],\"novelId\":123}", "{\"names\":[\"Aiko\",\"Aiko\"]}",
            "{\"names\":[\"Personaje de otra novela\"]}", "not-json"})
    void invalidCharacterToolArgumentsFailWithoutCachingTheGeneration(String arguments) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(characterToolCall("lookup_characters", arguments), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"panels\":[\"Una ventana vacia.\"]}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        var request = panels("Una ventana.").characters(java.util.List.of(
                new PromptCharacterBuilder().name("Aiko").description("Cabello oscuro.").build(),
                new PromptCharacterBuilder().name("Rin").description("Cabello claro.").build())).build();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(request))
                .withMessageContaining("personaje");
        assertThat(assistant.imagePanelPrompts(request)).map(this::panelDescription).containsExactly("Una ventana vacia.");
        server.verify();
    }

    @Test void characterLookupRejectsUnknownToolsAndRepeatedRounds() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(characterToolCall("change_character", "{\"names\":[\"Aiko\"]}"), MediaType.APPLICATION_JSON));
        for (int index = 0; index < 2; index++) {
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andRespond(withSuccess(characterToolCall("lookup_characters", "{\"names\":[\"Aiko\"]}"), MediaType.APPLICATION_JSON));
        }
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        var request = new StudioPromptRequestBuilder().from(studio("Aiko espera."))
                .availableCharacters(java.util.List.of(
                        new PromptCharacterBuilder().name("Aiko").description("Cabello oscuro.").build())).build();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(request))
                .withMessageContaining("herramienta de personajes no valida");
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(request))
                .withMessageContaining("limite de una consulta");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void characterToolCacheChangesWhenSheetsChangeOrAreDeleted(boolean studio) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String finalJson = studio ? "{\"prompt\":\"Aiko junto a la ventana.\"}"
                : "{\"panels\":[\"Aiko junto a la ventana.\"]}";
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        for (int index = 0; index < 3; index++) {
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andRespond(withSuccess(completion(finalJson), MediaType.APPLICATION_JSON));
        }
        for (String description : java.util.List.of("Ojos grises.", "Ojos verdes.", "")) {
            var cast = description.isEmpty() ? java.util.List.<local.higanbana.domain.ai.PromptCharacter>of()
                    : java.util.List.of(new PromptCharacterBuilder().name("Aiko").description(description).build());
            if (studio) {
                var request = new StudioPromptRequestBuilder().from(studio("Aiko espera.")).availableCharacters(cast).build();
                assistant.studioPrompt(request);
                assistant.studioPrompt(request);
            } else {
                var request = panels("Aiko espera.").characters(cast).build();
                assistant.imagePanelPrompts(request);
                assistant.imagePanelPrompts(request);
            }
        }
        server.verify();
    }

    @Test void oversizedCharacterToolResultsStopBeforeTheFinalModelRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(characterToolCall("lookup_characters", "{\"names\":[\"Aiko\",\"Rin\"]}"),
                        MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        var request = panels("Aiko y Rin esperan.").characters(java.util.List.of(
                new PromptCharacterBuilder().name("Aiko").description("a".repeat(1600)).build(),
                new PromptCharacterBuilder().name("Rin").description("b".repeat(1600)).build())).build();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(request))
                .withMessageContaining("3000 caracteres");
        server.verify();
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
        var chunks = assistant.chunks(chunks(source).limit(900).revision("Escenas breves").build());
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
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks(chunks("a".repeat(900)).limit(900).build()))
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
        assertThat(assistant.chunks(chunks(firstScene + secondScene).revision("Separar los cambios de tiempo").build()))
                .containsExactly(firstScene, secondScene);
        server.verify();
    }

    @Test void narrativeChunkingHasNoDemoFallback() {
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "", "test-model",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.chunkingAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks(chunks("Una escena.").build()))
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
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks(chunks("Una escena.").build()))
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
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.chunks(chunks("a".repeat(1800)).limit(900).build()))
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
        assertThat(assistant.imagePanelPrompts(panels("Una estacion.").direction("Luz lateral").build()))
                .map(this::panelDescription).containsExactly("Una viajera en primer plano, lluvia al fondo.");
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

        assertThat(assistant.imagePanelPrompts(panels("Aiko bajo la lluvia.").direction("Plano cercano.").build()))
                .map(this::panelDescription).containsExactly("Retrato en tinta negra.");
        assertThat(assistant.imagePanelPrompts(panels("Aiko bajo la lluvia.").direction("Plano cercano.").build()))
                .map(this::panelDescription).containsExactly("Retrato en tinta negra.");
        server.verify();
    }

    @Test void evictingStudioPromptForcesAnotherProviderRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var firstSeed = new java.util.concurrent.atomic.AtomicReference<Integer>();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("numero y orden de las viñetas")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("Conserva literalmente los dialogos")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString("cartelas de narrador")))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    firstSeed.set(new ObjectMapper().readTree(body).get("seed").intValue());
                })
                .andRespond(withSuccess(completion("{\"prompt\":\"Primera propuesta.\"}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertThat(new ObjectMapper().readTree(body).get("seed").intValue()).isNotEqualTo(firstSeed.get());
                })
                .andRespond(withSuccess(completion("{\"prompt\":\"Segunda propuesta.\"}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "nvidia/nemotron-3.5-lightning-30b-a3b",
                new ObjectMapper(), builder, new InMemoryAiResponseCache());
        var first = assistant.studioPrompt(studio("Una viñeta junto a la ventana."));
        assertThat(first.text()).contains("Primera propuesta.");
        assertThat(assistant.studioPrompt(studio("Una viñeta junto a la ventana."))).isEqualTo(first);
        assistant.evictCachedResponse(first.cacheKey());
        assertThat(assistant.studioPrompt(studio("Una viñeta junto a la ventana.")).text()).contains("Segunda propuesta.");
        server.verify();
    }

        @Test void lightningDisablesReasoningToLeaveTokensForTheResponse() throws Exception {
                RestClient.Builder builder = RestClient.builder();
                var server = MockRestServiceServer.bindTo(builder).build();
                server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                                .andExpect(jsonPath("$.model").value("nvidia/nemotron-3.5-lightning-30b-a3b"))
                                .andExpect(jsonPath("$.reasoning_effort").value("none"))
                                .andExpect(jsonPath("$.max_tokens").value(1800))
                                .andRespond(withSuccess(completion("{\"prompt\":\"Una ventana bajo la lluvia.\"}"), MediaType.APPLICATION_JSON));
                var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                                "nvidia/nemotron-3.5-lightning-30b-a3b", new ObjectMapper(), builder);
                assertThat(assistant.studioPrompt(studio("Una viñeta.")).text()).contains("Una ventana bajo la lluvia.");
                server.verify();
        }

    @Test void invalidStudioResponseIsNotCached() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"prompt\":null}"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion("{\"prompt\":\"Prompt valido.\"}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder, new InMemoryAiResponseCache());
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(studio("Una viñeta.")))
                .withMessageContaining("prompt final no valido");
        assertThat(assistant.studioPrompt(studio("Una viñeta.")).text()).contains("Prompt valido.");
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

        assertThat(assistant.chunks(chunks(source).revision("Agrupar la escena").build()))
                .containsExactly("Aiko espera. Rin llega.");
        assertThat(assistant.chunks(chunks(source).revision("Agrupar la escena").build()))
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
                new ObjectMapper(), builder, cache).imagePanelPrompts(panels("Aiko espera.").direction("Plano general.").build()))
                .map(this::panelDescription).containsExactly("Respuesta A.");
        assertThat(assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "model-b",
                new ObjectMapper(), builder, cache).imagePanelPrompts(panels("Aiko espera.").direction("Plano general.").build()))
                .map(this::panelDescription).containsExactly("Respuesta B.");
        assertThat(assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "model-a",
                new ObjectMapper(), builder, cache).imagePanelPrompts(panels("Aiko corre.").direction("Plano general.").build()))
                .map(this::panelDescription).containsExactly("Respuesta C.");
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

        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("Aiko espera.").count(2).build()));
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("Aiko espera.").count(2).build()));
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
        assertThat(assistant.imagePanelPrompts(panels("Una estacion.").direction("   ").build()))
                .map(this::panelDescription).containsExactly("Respuesta");
        assertThat(assistant.imagePanelPrompts(panels("Una estacion.").direction("Separa el salto temporal.").build()))
                .map(this::panelDescription).containsExactly("Respuesta");
        server.verify();
    }

    @Test void studioAlwaysIncludesDrawingAndLetteringRulesEvenIfTheModelOnlyDescribesTheScene() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Hablante -> globo: \"texto literal\"")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Recupera del texto original las intervenciones pertinentes")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Aiko dice: «Espera, Rin».")))
                .andRespond(withSuccess(completion("{\"prompt\":\"Aiko frente al faro bajo la lluvia.\"}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder);

        assertThat(assistant.studioPrompt(studio("Aiko dice: «Espera, Rin».")).text())
                .startsWith("Crea una imagen de manga japones en blanco y negro")
                .contains("tramas de semitonos", "cola hacia el personaje que habla", "Texto negro legible",
                        "sin las comillas ni el nombre del hablante", "para el narrador, sin cola",
                        "no añadir texto ni globos vacios")
                .contains("Aiko frente al faro bajo la lluvia.");
        server.verify();
    }

    @Test void panelPromptsPreserveLiteralLetteringAndSilentPanelsWithMandatoryDrawingRules() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        String spoken = "Aiko ante el faro. Aiko -> globo: \"Espera, Rin\". Narrador -> cartela: \"La lluvia golpea la puerta\".";
        String silent = "Rin contempla el mar. Sin rotulacion.";
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Incluye el texto concreto, no basta con pedir globos")))
                .andExpect(jsonPath("$.messages[0].content", org.hamcrest.Matchers.containsString(
                        "Si la viñeta no tiene texto pertinente, indica Sin rotulacion")))
                .andRespond(withSuccess(completion(new ObjectMapper().writeValueAsString(
                        Map.of("panels", java.util.List.of(spoken, silent)))), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder);

        var panels = assistant.imagePanelPrompts(panels("Aiko dice: «Espera, Rin». La lluvia golpea la puerta. Rin contempla el mar.")
                .count(2).build());
        assertThat(panels).allSatisfy(panel -> assertThat(panel)
                .startsWith("Crea una imagen de manga japones en blanco y negro")
                .contains("cola hacia el personaje que habla", "No inventar, parafrasear, traducir ni duplicar texto"));
        assertThat(panels.getFirst()).endsWith(spoken);
        assertThat(panels.getLast()).endsWith(silent);
        server.verify();
    }

    @Test void studioAssemblesReferencesDirectionAndCharactersFromStructuredData() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var request = new StudioPromptRequestBuilder()
                .panelPrompts(java.util.List.of("Aiko entra al faro.", "Rin espera junto a la ventana."))
                .source("Aiko entra al faro. Rin dice: «Te esperaba».")
                .direction("Luz lateral")
                .characters(java.util.List.of(new PromptCharacterBuilder()
                        .name("Aiko").description("Cabello corto, abrigo blanco.").build()))
                .build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andExpect(httpRequest -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) httpRequest).getBodyAsString();
                    String content = new ObjectMapper().readTree(body).get("messages").get(0).get("content").textValue();
                    assertThat(content).contains("exactamente 2 viñetas distintas",
                            "Viñeta 1:\nAiko entra al faro.\n\nViñeta 2:\nRin espera junto a la ventana.",
                            request.source(), "Direccion visual adicional: Luz lateral",
                            "Personajes:\nAiko: Cabello corto, abrigo blanco.");
                })
                .andRespond(withSuccess(completion("{\"prompt\":\"Rin -> globo: \\\"Te esperaba\\\".\"}"), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder);

        var prepared = assistant.studioPrompt(request);
        assertThat(prepared.text()).startsWith("Crea una imagen de manga japones en blanco y negro")
                .containsOnlyOnce("Crea una imagen de manga japones en blanco y negro")
                .contains("Rin -> globo: \"Te esperaba\".", "Referencias de los paneles y texto original",
                        "exactamente 2 viñetas", "Viñeta 1:\nAiko entra al faro.",
                        "Viñeta 2:\nRin espera junto a la ventana.", request.source())
                .endsWith("Direccion visual adicional: Luz lateral");
        assertThat(assistant.imagePrompt(prepared.text(), assistant.characterContext(request.characters())))
                .startsWith(prepared.text())
                .endsWith("Personajes:\nAiko: Cabello corto, abrigo blanco.");
        server.verify();
    }

    @Test void oversizeStudioReferencesAndCharacterSnapshotsAreRejectedBeforeCallingTheProvider() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(studio("x".repeat(30000))))
                .withMessageContaining("30000 caracteres");
        var request = new StudioPromptRequestBuilder().from(studio("Una escena."))
                .characters(java.util.List.of(new PromptCharacterBuilder().name("Aiko")
                        .description("x".repeat(3000)).build())).build();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(request))
                .withMessageContaining("Selecciona menos personajes");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {1500, 10000})
    void mandatoryInstructionsFitWithinTheExistingOutputLimitAndOversizeResponsesAreRejected(int limit) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var mapper = new ObjectMapper();
        var descriptionLimit = new java.util.concurrent.atomic.AtomicInteger();
        String field = limit == 1500 ? "panels" : "prompt";
        for (int extra : new int[]{0, 1}) {
            server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                    .andExpect(request -> {
                        String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                        var matcher = java.util.regex.Pattern.compile("maximo (\\d+) caracteres")
                                .matcher(mapper.readTree(body).get("messages").get(0).get("content").textValue());
                        assertThat(matcher.find()).isTrue();
                        descriptionLimit.set(Integer.parseInt(matcher.group(1)));
                    })
                    .andRespond(request -> {
                        String text = "x".repeat(descriptionLimit.get() + extra);
                        Object value = limit == 1500 ? java.util.List.of(text) : text;
                        return withSuccess(completion(mapper.writeValueAsString(Map.of(field, value))), MediaType.APPLICATION_JSON)
                                .createResponse(request);
                    });
        }
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model", mapper, builder);

        String result = limit == 1500 ? assistant.imagePanelPrompts(panels("Una escena.").build()).getFirst()
                : assistant.studioPrompt(studio("Una escena.")).text();
        String generated = limit == 1500 ? result : result.substring(0, result.indexOf("\n\nReferencias de los paneles"));
        assertThat(generated).hasSize(limit);
        assertThatIllegalArgumentException().isThrownBy(() -> {
            if (limit == 1500) { assistant.imagePanelPrompts(panels("Otra escena.").build()); }
            else { assistant.studioPrompt(studio("Otra escena.")); }
        }).withMessageContaining(limit == 1500 ? "prompts de imagen no validos" : "prompt final no valido");
        server.verify();
    }

    @Test void emptyNvidiaTextIsRejectedByOptionalResponseExtraction() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withSuccess(completion(""), MediaType.APPLICATION_JSON));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);

                assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("Una estacion.").build()))
                        .withMessageContaining("prompts de imagen no validos");
        server.verify();
    }

    @Test void rejectsOversizeContextWithoutSendingRequest() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key",
                "test-model", new ObjectMapper(), builder);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("a".repeat(8001)).build()));
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("context").direction("a".repeat(1501)).build()));
        server.verify();
    }

    @Test void providerFailureNeverFallsBackToDemoOrRevealsKey() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "private-test-key",
                "test-model", new ObjectMapper(), builder);
                assertThatThrownBy(() -> assistant.imagePanelPrompts(panels("context").build()))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credenciales")
                        .hasMessageNotContaining("private-test-key");
        server.verify();
    }

    @Test void nvidiaOverloadIsReportedWithoutProviderBodyOrCachedResult() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://integrate.api.nvidia.com/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private provider details"));
        var cache = new InMemoryAiResponseCache();
        var assistant = assistant("nvidia", "https://integrate.api.nvidia.com", "test-key", "test-model",
                new ObjectMapper(), builder, cache);
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.studioPrompt(studio("Una viñeta.")))
                .withMessageContaining("temporalmente sobrecargado")
                .withMessageNotContaining("private provider details");
        server.verify();
    }

    @Test void imagePanelPromptsAreUnavailableWithoutNvidiaAndNeverUseDemoText() {
        var assistant = assistant("demo", "https://integrate.api.nvidia.com", "", "unused",
                new ObjectMapper(), RestClient.builder());
        assertThat(assistant.provenance()).contains("DEMO", "sin IA");
        assertThat(assistant.imagePanelPromptsAvailable()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> assistant.imagePanelPrompts(panels("Fin.").build()))
                .withMessageContaining("requiere NVIDIA");
    }
}