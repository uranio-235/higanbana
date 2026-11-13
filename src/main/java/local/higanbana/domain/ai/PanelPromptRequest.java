package local.higanbana.domain.ai;

import java.util.List;
import org.immutables.value.Value;

@Value.Builder
public record PanelPromptRequest(String source, int count, String direction,
                                 List<PromptCharacter> characters) implements WithPanelPromptRequest {
    public PanelPromptRequest {
        characters = List.copyOf(characters);
    }
}
