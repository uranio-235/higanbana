package local.higanbana.domain;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Version;

@MappedSuperclass
@SequenceGenerator(name = "row_id_seq", sequenceName = "row_id_seq", allocationSize = 1)
public abstract class Row {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "row_id_seq")
    @Column(columnDefinition = "bigint")
    public Long id;
    @Version public long version;
}