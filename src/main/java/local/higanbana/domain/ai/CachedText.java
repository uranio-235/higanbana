package local.higanbana.domain.ai;

import java.util.List;
import org.immutables.value.Value;

@Value.Builder
public record CachedText(String text, String cacheKey, List<PromptCharacter> characters) implements WithCachedText {
    public CachedText {
        characters = List.copyOf(characters);
    }
}