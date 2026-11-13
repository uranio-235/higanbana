package local.higanbana.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import local.higanbana.domain.ai.PromptCharacter;
import local.higanbana.domain.ai.TextProviderException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.lang.NonNull;

final class CharacterLookup implements ToolCallback {
    @NonNull private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("lookup_characters")
            .description("Consulta en una sola llamada las fichas de identidad visual de los personajes presentes. "
                    + "Usa los nombres exactos del catalogo de esta novela. Solo lectura; no genera ni modifica fichas.")
            .inputSchema("""
                    {"type":"object","properties":{"names":{"type":"array","items":{"type":"string"},
                    "minItems":1,"uniqueItems":true}},"required":["names"],"additionalProperties":false}
                    """)
            .build();
    private final List<PromptCharacter> available;
    private final ObjectMapper mapper;
    private List<PromptCharacter> characters = List.of();

    CharacterLookup(List<PromptCharacter> available, ObjectMapper mapper) {
        this.available = List.copyOf(available);
        this.mapper = mapper;
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
            List<PromptCharacter> requested = new ArrayList<>();
            for (var name : root.path("names")) {
                if (!name.isTextual()) {
                    throw new TextProviderException("NVIDIA debe consultar personajes por sus nombres exactos.");
                }
                PromptCharacter character = available.stream().filter(item -> item.name().equals(name.textValue()))
                        .findFirst().orElseThrow(() -> new TextProviderException(
                                "NVIDIA solicito un personaje que no figura en el catalogo de esta novela."));
                if (requested.contains(character)) {
                    throw new TextProviderException("NVIDIA solicito personajes duplicados en la consulta.");
                }
                requested.add(character);
            }
            String result = mapper.writeValueAsString(requested);
            characters = List.copyOf(requested);
            return result;
        } catch (JsonProcessingException exception) {
            throw new TextProviderException("No se pudo interpretar la consulta de personajes de NVIDIA.", exception);
        }
    }

    List<PromptCharacter> characters() { return characters; }
}
