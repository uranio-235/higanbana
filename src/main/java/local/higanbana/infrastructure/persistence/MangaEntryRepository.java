package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.MangaEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MangaEntryRepository extends JpaRepository<MangaEntry, Long> {
    @Query("SELECT entry FROM MangaEntry entry WHERE entry.novelId = :novelId ORDER BY entry.narrativeOrder, entry.id")
    List<MangaEntry> findEntriesForNovel(@Param("novelId") long novelId);
}
