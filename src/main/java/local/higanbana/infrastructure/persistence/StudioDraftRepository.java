package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.StudioDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudioDraftRepository extends JpaRepository<StudioDraft, Long> {
    @Query("SELECT draft FROM StudioDraft draft WHERE draft.novelId = :novelId ORDER BY draft.id DESC")
    List<StudioDraft> findDraftsForNovel(@Param("novelId") long novelId);
}
