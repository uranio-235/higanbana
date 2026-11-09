package local.higanbana.domain;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "PanelPlan")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "chunkId"))
public class PanelPlan extends Row {
    public Long novelId;
    public Long chunkId;
    public boolean confirmed;
    public int panelCount;
    public String provenance;
    @ElementCollection @OrderColumn(name = "panel_order") @Column(columnDefinition = "text")
    @CollectionTable(name = "panel_plan_descriptions", joinColumns = @JoinColumn(name = "panel_plan_id", columnDefinition = "bigint"))
    public List<String> descriptions = new ArrayList<>();
}