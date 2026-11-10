package local.higanbana.infrastructure.image;

public interface ImageProvider {
    ImageResult generate(String prompt);

    record ImageResult(byte[] bytes, String mediaType, String provenance) {}
}