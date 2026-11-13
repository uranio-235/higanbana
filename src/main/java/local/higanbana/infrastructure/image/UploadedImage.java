package local.higanbana.infrastructure.image;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class UploadedImage {
    private UploadedImage() {}

    public static ImageProvider.ImageResult decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 20_000_000) {
            throw new IllegalArgumentException("La imagen debe tener un maximo de 20 MB.");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw new IllegalArgumentException("Solo se admiten imagenes PNG o JPEG validas."); }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("png", "jpeg", "jpg").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000) {
                    throw new IllegalArgumentException("La imagen debe ser PNG o JPEG y no superar 40 megapixeles.");
                }
                var decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (decoded == null || !ImageIO.write(decoded, format, output) || output.size() > 50_000_000) {
                    throw new IllegalArgumentException("No se pudo procesar la imagen o supera el limite de almacenamiento de 50 MB.");
                }
                return new ImageResultBuilder().bytes(output.toByteArray())
                        .mediaType(format.equals("png") ? "image/png" : "image/jpeg")
                        .provenance("Imagen subida por el usuario").build();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("La imagen no es valida o esta incompleta.", exception);
        }
    }
}
