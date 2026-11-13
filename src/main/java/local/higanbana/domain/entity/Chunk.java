package local.higanbana.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity(name = "Chunk")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"novelId", "position"}))
public class Chunk extends Row {
    public Long novelId;
    public int position;
    @Column(columnDefinition = "text") public String content;
    @Column(name = "characters_extracted_at")
    public LocalDateTime charactersExtractedAt;
}