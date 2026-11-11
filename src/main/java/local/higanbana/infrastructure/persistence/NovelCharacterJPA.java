package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.NovelCharacter;
import org.springframework.stereotype.Component;

@Component
class NovelCharacterJPA {
    private final NovelCharacterRepository repository;

    NovelCharacterJPA(NovelCharacterRepository repository) { this.repository = repository; }

    List<NovelCharacter> findCharactersForNovel(long novelId) { return repository.findCharactersForNovel(novelId); }
    Optional<NovelCharacter> findCharacterForNovel(long novelId, long characterId) {
        return repository.findCharacterForNovel(novelId, characterId);
    }
    void save(NovelCharacter character) { repository.save(character); }
}
