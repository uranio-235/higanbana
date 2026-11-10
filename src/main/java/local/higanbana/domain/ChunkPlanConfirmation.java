package local.higanbana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "ChunkPlanConfirmation")
@Table(name = "chunk_plan_confirmation")
public class ChunkPlanConfirmation {
    @Id
    @Column(name = "chunk_plan_id", columnDefinition = "bigint")
    public Long chunkPlanId;

    protected ChunkPlanConfirmation() {}

    public ChunkPlanConfirmation(Long chunkPlanId) {
        this.chunkPlanId = chunkPlanId;
    }
}