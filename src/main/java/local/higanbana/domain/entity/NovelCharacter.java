package local.higanbana.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity(name = "NovelCharacter")
@Table(name = "novel_character")
public class NovelCharacter extends Row {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "novel_id", nullable = false, columnDefinition = "bigint")
    public Novel novel;
    @Column(nullable = false, length = 120)
    public String name;
    @Column(nullable = false, columnDefinition = "text")
    public String description;
}