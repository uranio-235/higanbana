package local.higanbana.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;

@Entity(name = "StudioDraft")
public class StudioDraft extends Row {
    public Long novelId;
    public Long firstPanelId;
    public Long secondPanelId;
    public int narrativeOrder;
    @Column(columnDefinition = "text") public String prompt;
    @Column(columnDefinition = "text") public String characterContext = "";
    public String provenance;
}