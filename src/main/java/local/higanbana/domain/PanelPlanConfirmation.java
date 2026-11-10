package local.higanbana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "PanelPlanConfirmation")
@Table(name = "panel_plan_confirmation")
public class PanelPlanConfirmation {
    @Id
    @Column(name = "panel_plan_id", columnDefinition = "bigint")
    public Long panelPlanId;
    public String provenance;

    protected PanelPlanConfirmation() {}

    public PanelPlanConfirmation(Long panelPlanId, String provenance) {
        this.panelPlanId = panelPlanId;
        this.provenance = provenance;
    }
}