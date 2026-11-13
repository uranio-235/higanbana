package local.higanbana.domain.ai;

import java.util.List;
import org.immutables.value.Value;

@Value.Builder
public record StudioPromptRequest(List<String> panelPrompts, String source, String direction,
                                 List<PromptCharacter> characters) implements WithStudioPromptRequest {
    public StudioPromptRequest {
        panelPrompts = List.copyOf(panelPrompts);
        characters = List.copyOf(characters);
    }
}
