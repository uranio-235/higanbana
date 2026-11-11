package local.higanbana.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity(name = "Panel")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chunkId", "position"}))
public class Panel extends Row {
    public Long novelId;
    public Long chunkId;
    public int chunkPosition;
    public int position;
    @Column(name = "description", columnDefinition = "text") public String imagePrompt;
}