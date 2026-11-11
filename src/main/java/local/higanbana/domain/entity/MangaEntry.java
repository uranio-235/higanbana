package local.higanbana.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "MangaEntry")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "candidateId"))
public class MangaEntry extends Row {
    public Long novelId;
    public Long candidateId;
    public Long firstPanelId;
    public Long secondPanelId;
    public int narrativeOrder;
    public String filename;
    public String mediaType;
    public String provenance;
    @Column(columnDefinition = "text") public String caption;
}