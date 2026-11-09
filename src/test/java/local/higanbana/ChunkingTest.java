package local.higanbana;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChunkingTest {
    @Test
    void preservesEveryCharacterAndBoundsEveryChunk() {
        String text = "  Inicio\r\n\nUna escena \uD83C\uDF19 con espacios.  \n".repeat(1000);
        for (int limit : new int[]{2, 17, 200, 2000, 8000}) {
            var chunks = Chunking.split(text, limit);
            assertThat(String.join("", chunks)).isEqualTo(text);
            assertThat(chunks).allSatisfy(chunk -> {
                assertThat(chunk.length()).isBetween(1, limit);
                assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
            });
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatIllegalArgumentException().isThrownBy(() -> Chunking.split(" ", 200));
        assertThatIllegalArgumentException().isThrownBy(() -> Chunking.split("text", 1));
        assertThatIllegalArgumentException().isThrownBy(() -> Chunking.split("text", 8001));
    }
}