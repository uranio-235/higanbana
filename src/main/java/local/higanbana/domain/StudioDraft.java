package local.higanbana.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "StudioDraft")
public class StudioDraft extends Row {
    public Long novelId;
    public Long firstPanelId;
    public Long secondPanelId;
    @ElementCollection @OrderColumn(name = "panel_order") @Column(name = "panel_id", columnDefinition = "bigint")
    @CollectionTable(name = "studio_draft_panels", joinColumns = @JoinColumn(name = "studio_draft_id", columnDefinition = "bigint"))
    public List<Long> panelIds = new ArrayList<>();
    public int narrativeOrder;
    @Column(columnDefinition = "text") public String prompt;
    @Column(columnDefinition = "text") public String characterContext = "";
    public String aiCacheKey;
    public String provenance;
}