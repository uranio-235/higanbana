package local.higanbana.domain.ai;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Builder
public record PanelPromptRequest(String source, int count, String direction,
                                 List<PromptCharacter> characters, Optional<String> memoryScope) implements WithPanelPromptRequest {
    public PanelPromptRequest {
        characters = List.copyOf(characters);
        memoryScope = memoryScope == null ? Optional.empty() : memoryScope;
    }
}
