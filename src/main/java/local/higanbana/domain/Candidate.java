package local.higanbana.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "Candidate")
public class Candidate extends Row {
    public Long novelId;
    public Long draftId;
    public Long firstPanelId;
    public Long secondPanelId;
    public int narrativeOrder;
    @Column(columnDefinition = "text") public String prompt;
    @Enumerated(EnumType.STRING) public ReviewState state = ReviewState.PENDING;
    @JdbcTypeCode(SqlTypes.VARBINARY) @Column(columnDefinition = "blob") public byte[] image;
    public String mediaType;
    public String provenance;
}