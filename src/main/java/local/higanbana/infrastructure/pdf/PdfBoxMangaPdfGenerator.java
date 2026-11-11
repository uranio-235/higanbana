package local.higanbana.infrastructure.pdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import local.higanbana.domain.export.MangaPdfGenerator;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxMangaPdfGenerator implements MangaPdfGenerator {
    private static final float MAX_PAGE_SIDE_POINTS = 842f;

    @Override
    public byte[] generate(List<PanelImage> panels) {
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("El PDF debe contener al menos un panel.");
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (PanelImage panel : panels) {
                byte[] imageBytes = panel.mediaType().equals("image/svg+xml") ? rasterizeSvg(panel.bytes()) : panel.bytes();
                if (!panel.mediaType().equals("image/svg+xml")
                        && !panel.mediaType().equals("image/png")
                        && !panel.mediaType().equals("image/jpeg")) {
                    throw new IllegalArgumentException("Formato de imagen no compatible con la exportacion PDF.");
                }

                PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "panel");
                float scale = MAX_PAGE_SIDE_POINTS / Math.max(image.getWidth(), image.getHeight());
                float pageWidth = image.getWidth() * scale;
                float pageHeight = image.getHeight() * scale;
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(image, 0, 0, pageWidth, pageHeight);
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el PDF de la novela.", exception);
        }
    }

    private byte[] rasterizeSvg(byte[] svgBytes) throws IOException {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, Boolean.FALSE);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            transcoder.transcode(
                    new TranscoderInput(new ByteArrayInputStream(svgBytes)),
                    new TranscoderOutput(output));
            return output.toByteArray();
        } catch (TranscoderException exception) {
            throw new IOException("No se pudo convertir un panel SVG para el PDF.", exception);
        }
    }
}
