package local.higanbana.domain.export;

import org.immutables.value.Value;

import java.util.List;

public interface MangaPdfGenerator {
    byte[] generate(List<PanelImage> panels);

    @Value.Builder
    record PanelImage(byte[] bytes, String mediaType) implements WithPanelImage {}
}
