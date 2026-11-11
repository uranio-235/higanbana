package local.higanbana.infrastructure.persistence;

import java.util.List;
import local.higanbana.domain.entity.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NovelRepository extends JpaRepository<Novel, Long> {
    @Query("SELECT novel FROM Novel novel ORDER BY novel.id DESC")
    List<Novel> findNovels();
}
