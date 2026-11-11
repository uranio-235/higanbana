package local.higanbana.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "Novel")
public class Novel extends Row {
    public String title;
    @Column(columnDefinition = "text") public String source;
    @OneToMany(mappedBy = "novel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    public List<NovelCharacter> characters = new ArrayList<>();

    public void addCharacter(NovelCharacter character) {
        characters.add(character);
        character.novel = this;
    }

    public void removeCharacter(NovelCharacter character) {
        characters.remove(character);
        character.novel = null;
    }
}