package local.higanbana;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;
import local.higanbana.domain.export.MangaPdfGenerator.PanelImage;
import local.higanbana.infrastructure.pdf.PdfBoxMangaPdfGenerator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PdfBoxMangaPdfGeneratorTest {
    private final PdfBoxMangaPdfGenerator generator = new PdfBoxMangaPdfGenerator();

    @Test
    void createsOnePageForEachPngOrSvgPanel() throws Exception {
        ByteArrayOutputStream pngOutput = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "png", pngOutput);
        byte[] svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="4" height="2">
                  <rect width="4" height="2" fill="#ffffff"/>
                </svg>
                """.getBytes(StandardCharsets.UTF_8);

        byte[] bytes = generator.generate(List.of(
                new PanelImage(pngOutput.toByteArray(), "image/png"),
                new PanelImage(svg, "image/svg+xml")));

        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                var page = document.getPage(index).getMediaBox();
                assertThat(page.getWidth() / page.getHeight()).isCloseTo(2f,
                        org.assertj.core.data.Offset.offset(0.01f));
            }
        }
    }

    @Test
    void rejectsUnsupportedImageTypes() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                generator.generate(List.of(new PanelImage(new byte[]{1}, "image/gif"))))
                .withMessageContaining("Formato de imagen");
    }
}
