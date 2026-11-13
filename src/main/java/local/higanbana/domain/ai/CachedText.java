package local.higanbana.domain.ai;

import org.immutables.value.Value;

@Value.Builder
public record CachedText(String text, String cacheKey) implements WithCachedText {}