package local.higanbana.infrastructure.ai;

import java.util.List;
import java.util.function.Predicate;
import local.higanbana.domain.ai.PromptCharacter;
import org.immutables.value.Value;

@Value.Builder
record TextRequest(String task, String context, String revision, String operation,
                   Predicate<String> validator, int contextLimit,
                   List<PromptCharacter> characters) implements WithTextRequest {
    TextRequest {
        characters = List.copyOf(characters);
    }
}
