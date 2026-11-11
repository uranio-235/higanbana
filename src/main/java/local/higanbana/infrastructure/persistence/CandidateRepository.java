package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    @Query("SELECT candidate FROM Candidate candidate WHERE candidate.draftId = :draftId")
    List<Candidate> findCandidatesForDraft(@Param("draftId") long draftId);

    @Query("SELECT candidate FROM Candidate candidate WHERE candidate.novelId = :novelId ORDER BY candidate.id DESC")
    List<Candidate> findCandidatesForNovel(@Param("novelId") long novelId);
}
