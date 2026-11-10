package local.higanbana;

import local.higanbana.domain.Chunking;
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

    @Test
    void analysisUnitsKeepSentencesAndParagraphsWithoutCharacterQuotas() {
        String source = "Aiko espera. Rin llega.\r\n\r\nAl dia siguiente viajan. ";
        var windows = Chunking.analysisWindows(source, 5000);
        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst()).contains("Aiko espera. ");
        assertThat(String.join("", windows.getFirst())).isEqualTo(source);
    }

    @Test
    void transportWindowsBoundModelInputWithoutLosingWhitespaceOrUnicode() {
        String source = "  Aiko espera.\r\nRin llega \uD83C\uDF19.\n\n".repeat(900);
        for (int limit : new int[]{200, 5000}) {
            var windows = Chunking.analysisWindows(source, limit);
            assertThat(String.join("", windows.stream().flatMap(java.util.Collection::stream).toList())).isEqualTo(source);
            assertThat(windows).allSatisfy(units -> {
                assertThat(units.size()).isBetween(1, 40);
                assertThat(String.join("", units).length()).isLessThanOrEqualTo(limit);
            });
        }
    }
}