package local.higanbana.domain.ai;

import org.immutables.value.Value;

@Value.Builder
public record ChunkRequest(String source, int limit, String revision) implements WithChunkRequest {}
