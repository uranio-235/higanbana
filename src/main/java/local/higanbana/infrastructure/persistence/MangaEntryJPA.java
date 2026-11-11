package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.MangaEntry;
import org.springframework.stereotype.Component;

@Component
class MangaEntryJPA {
    private final MangaEntryRepository repository;

    MangaEntryJPA(MangaEntryRepository repository) { this.repository = repository; }

    List<MangaEntry> findEntriesForNovel(long novelId) { return repository.findEntriesForNovel(novelId); }
    Optional<MangaEntry> findById(long id) { return repository.findById(id); }
    void save(MangaEntry entry) { repository.save(entry); }
}
