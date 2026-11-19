package local.higanbana.domain.ai;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Builder
public record StudioPromptRequest(List<String> panelPrompts, String source, String direction,
                                 List<PromptCharacter> characters,
                                 List<PromptCharacter> availableCharacters,
                                 Optional<String> memoryScope) implements WithStudioPromptRequest {
    public StudioPromptRequest {
        panelPrompts = List.copyOf(panelPrompts);
        characters = List.copyOf(characters);
        availableCharacters = List.copyOf(availableCharacters);
        memoryScope = memoryScope == null ? Optional.empty() : memoryScope;
    }
}
