package local.higanbana.domain.entity;

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

@Entity(name = "ChunkPlan")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "novelId"))
public class ChunkPlan extends Row {
    public Long novelId;
    public int chunkLimit;
    @Column(updatable = false) public boolean confirmed;
    @Column(columnDefinition = "text") public String advice;
    @ElementCollection @OrderColumn(name = "segment_order") @Column(columnDefinition = "text")
    @CollectionTable(name = "chunk_plan_segments", joinColumns = @JoinColumn(name = "chunk_plan_id", columnDefinition = "bigint"))
    public List<String> segments = new ArrayList<>();
}