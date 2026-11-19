package local.higanbana.domain.memory;

public interface TextEmbedder {
    float[] embed(String text);
}