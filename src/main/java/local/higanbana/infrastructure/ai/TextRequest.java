package local.higanbana.infrastructure.ai;

import java.util.function.Predicate;
import org.immutables.value.Value;

@Value.Builder
record TextRequest(String task, String context, String revision, String operation,
                   Predicate<String> validator, int contextLimit) implements WithTextRequest {}
