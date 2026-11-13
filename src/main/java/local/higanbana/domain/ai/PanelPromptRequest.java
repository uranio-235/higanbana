package local.higanbana.domain.ai;

import org.immutables.value.Value;

@Value.Builder
public record PanelPromptRequest(String source, int count, String direction) implements WithPanelPromptRequest {}
