package local.higanbana.infrastructure.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LocalOnnxTextEmbedderTest {
    @Test
    void runsTheConfiguredMultilingualModelLocally() {
        String onnxPath = System.getProperty("higanbana.memory.onnx",
            System.getenv().getOrDefault("HIGANBANA_MEMORY_ONNX", "./memory-model/model.onnx"));
        String tokenizerPath = System.getProperty("higanbana.memory.tokenizer",
            System.getenv().getOrDefault("HIGANBANA_MEMORY_TOKENIZER", "./memory-model/tokenizer.json"));
        assumeTrue(Files.isRegularFile(Path.of(onnxPath)) && Files.isRegularFile(Path.of(tokenizerPath)),
            "Run memory-model/download-model.ps1 or configure local model paths.");

        var embedder = new LocalOnnxTextEmbedder(onnxPath, tokenizerPath);
        try {
            float[] first = embedder.embed("Una joven de cabello oscuro camina bajo la lluvia.");
            float[] paraphrase = embedder.embed("Una muchacha de pelo negro anda bajo la lluvia.");
            float[] unrelated = embedder.embed("Un avion cruza el cielo sobre el oceano.");

            assertThat(first).hasSize(384);
            assertThat(IntStream.range(0, first.length).mapToDouble(index -> first[index]).allMatch(Double::isFinite))
                    .isTrue();
            assertThat(cosine(first, paraphrase)).isGreaterThan(cosine(first, unrelated));
        } finally {
            embedder.close();
        }
    }

    private double cosine(float[] left, float[] right) {
        double product = 0;
        for (int index = 0; index < left.length; index++) {
            product += (double) left[index] * right[index];
        }
        return product;
    }
}