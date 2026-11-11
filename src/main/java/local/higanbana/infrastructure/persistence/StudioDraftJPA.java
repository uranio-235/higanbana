package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.StudioDraft;
import org.springframework.stereotype.Component;

@Component
class StudioDraftJPA {
    private final StudioDraftRepository repository;

    StudioDraftJPA(StudioDraftRepository repository) { this.repository = repository; }

    List<StudioDraft> findDraftsForNovel(long novelId) { return repository.findDraftsForNovel(novelId); }
    Optional<StudioDraft> findById(long id) { return repository.findById(id); }
    void save(StudioDraft draft) { repository.save(draft); }
    void delete(StudioDraft draft) { repository.delete(draft); }
}
