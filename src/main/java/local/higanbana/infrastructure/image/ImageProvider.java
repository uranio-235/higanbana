package local.higanbana.infrastructure.image;

public interface ImageProvider {
    record ImageResult(byte[] bytes, String mediaType, String provenance) {}
}