package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.Candidate;
import org.springframework.stereotype.Component;

@Component
class CandidateJPA {
    private final CandidateRepository repository;

    CandidateJPA(CandidateRepository repository) { this.repository = repository; }

    List<Candidate> findCandidatesForDraft(long draftId) { return repository.findCandidatesForDraft(draftId); }
    List<Candidate> findCandidatesForNovel(long novelId) { return repository.findCandidatesForNovel(novelId); }
    Optional<Candidate> findById(long id) { return repository.findById(id); }
    void save(Candidate candidate) { repository.save(candidate); }
}
