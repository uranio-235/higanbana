package local.higanbana.domain.export;

import java.util.List;

public interface MangaPdfGenerator {
    byte[] generate(List<PanelImage> panels);

    record PanelImage(byte[] bytes, String mediaType) {}
}
