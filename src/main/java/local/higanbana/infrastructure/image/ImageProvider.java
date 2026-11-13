package local.higanbana.infrastructure.image;

import org.immutables.value.Value;

public interface ImageProvider {
    @Value.Builder
    record ImageResult(byte[] bytes, String mediaType, String provenance) implements WithImageResult {}
}