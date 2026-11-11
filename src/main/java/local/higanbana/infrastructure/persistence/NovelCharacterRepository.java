package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.NovelCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NovelCharacterRepository extends JpaRepository<NovelCharacter, Long> {
    @Query("SELECT character FROM NovelCharacter character WHERE character.novel.id = :novelId ORDER BY character.id")
    List<NovelCharacter> findCharactersForNovel(@Param("novelId") long novelId);

    @Query("SELECT character FROM NovelCharacter character WHERE character.id = :characterId AND character.novel.id = :novelId")
    Optional<NovelCharacter> findCharacterForNovel(@Param("novelId") long novelId,
                                                  @Param("characterId") long characterId);
}
