package local.higanbana.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import local.higanbana.application.MemoryService;
import local.higanbana.domain.ai.PromptCharacter;
import local.higanbana.domain.ai.PromptCharacterBuilder;
import local.higanbana.domain.ai.TextProviderException;
import local.higanbana.domain.memory.MemoryMatch;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.NonNull;

final class CharacterLookup implements ToolCallback {
    private static final int MEMORY_RESULTS = 3;
    @NonNull private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("lookup_characters")
        .description("Busca en una sola llamada recuerdos relevantes de los personajes presentes por similitud semantica. "
            + "Usa los nombres exactos del catalogo de esta novela. Solo lectura; no genera ni modifica fichas.")
            .inputSchema("""
                    {"type":"object","properties":{"names":{"type":"array","items":{"type":"string"},
                    "minItems":1,"uniqueItems":true}},"required":["names"],"additionalProperties":false}
                    """)
            .build();
    private final List<PromptCharacter> available;
    private final String memoryScope;
    private final MemoryService memories;
    private final ObjectMapper mapper;
    private List<PromptCharacter> characters = List.of();

    CharacterLookup(List<PromptCharacter> available, String memoryScope, MemoryService memories, ObjectMapper mapper) {
        this.available = List.copyOf(available);
        this.memoryScope = memoryScope == null || memoryScope.isBlank() ? "prompt" : memoryScope;
        this.memories = memories;
        this.mapper = mapper;
    }

    void seed() {
        for (PromptCharacter character : available) {
            memories.remember(characterScope(character.name()), memoryText(character));
        }
    }

    @Override @NonNull public ToolDefinition getToolDefinition() { return DEFINITION; }

    @Override @NonNull public String call(@NonNull String arguments) {
        try {
            var root = mapper.readTree(arguments);
            if (root == null || !root.isObject() || root.size() != 1
                    || !root.path("names").isArray() || root.path("names").isEmpty()
                    || root.path("names").size() > available.size()) {
                throw new TextProviderException("NVIDIA solicito una consulta de personajes no valida; no se guardaron cambios.");
            }
            List<PromptCharacter> requested = new java.util.ArrayList<>();
            List<Map<String, Object>> results = new java.util.ArrayList<>();
            for (var name : root.path("names")) {
                if (!name.isTextual()) {
                    throw new TextProviderException("NVIDIA debe consultar personajes por sus nombres exactos.");
                }
                PromptCharacter character = available.stream().filter(item -> item.name().equals(name.textValue()))
                        .findFirst().orElseThrow(() -> new TextProviderException(
                                "NVIDIA solicito un personaje que no figura en el catalogo de esta novela."));
                if (requested.stream().anyMatch(item -> item.name().equals(character.name()))) {
                    throw new TextProviderException("NVIDIA solicito personajes duplicados en la consulta.");
                }
                List<MemoryMatch> matches = memories.search(characterScope(character.name()),
                    character.name() + "\n" + character.description(), MEMORY_RESULTS);
                if (matches.isEmpty()) {
                    throw new TextProviderException("No se encontraron recuerdos para un personaje solicitado.");
                }
                List<String> descriptions = matches.stream().map(match -> description(character.name(), match.content()))
                    .distinct().toList();
                requested.add(new PromptCharacterBuilder().name(character.name())
                    .description(String.join("\n", descriptions)).build());
                results.add(Map.of("name", character.name(), "memories", matches.stream()
                    .map(match -> Map.of("content", description(character.name(), match.content()),
                        "similarity", match.similarity())).toList()));
            }
                String result = mapper.writeValueAsString(Map.of("characters", results));
            characters = List.copyOf(requested);
            return result;
        } catch (JsonProcessingException exception) {
            throw new TextProviderException("No se pudo interpretar la consulta de personajes de NVIDIA.", exception);
        }
    }

    List<PromptCharacter> characters() { return characters; }

    private String characterScope(String name) {
        return memoryScope + ":character:" + name;
    }

    private String memoryText(PromptCharacter character) {
        return "Nombre: " + character.name() + "\nFicha: " + character.description();
    }

    private String description(String name, String content) {
        String prefix = "Nombre: " + name + "\nFicha: ";
        if (!content.startsWith(prefix)) {
            throw new TextProviderException("Un recuerdo recuperado no coincide con el personaje solicitado.");
        }
        return content.substring(prefix.length());
    }
}
