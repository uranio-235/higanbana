package local.higanbana.domain.ai;

import org.immutables.value.Value;

@Value.Builder
public record PromptCharacter(String name, String description) implements WithPromptCharacter {}
