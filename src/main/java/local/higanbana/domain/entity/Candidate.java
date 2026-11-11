package local.higanbana.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;
import local.higanbana.domain.ReviewState;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "Candidate")
public class Candidate extends Row {
    public Long novelId;
    public Long draftId;
    public Long firstPanelId;
    public Long secondPanelId;
    @ElementCollection @OrderColumn(name = "panel_order") @Column(name = "panel_id", columnDefinition = "bigint")
    @CollectionTable(name = "candidate_panels", joinColumns = @JoinColumn(name = "candidate_id", columnDefinition = "bigint"))
    public List<Long> panelIds = new ArrayList<>();
    public int narrativeOrder;
    @Column(columnDefinition = "text") public String prompt;
    @Enumerated(EnumType.STRING) public ReviewState state = ReviewState.PENDING;
    @JdbcTypeCode(SqlTypes.VARBINARY) @Column(columnDefinition = "blob") public byte[] image;
    public String mediaType;
    public String provenance;
}