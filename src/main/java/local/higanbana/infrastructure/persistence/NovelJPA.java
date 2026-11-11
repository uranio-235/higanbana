package local.higanbana.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.Novel;
import org.springframework.stereotype.Component;

@Component
class NovelJPA {
    private final NovelRepository repository;

    NovelJPA(NovelRepository repository) { this.repository = repository; }

    List<Novel> findNovels() { return repository.findNovels(); }
    Optional<Novel> findById(long id) { return repository.findById(id); }
    void save(Novel novel) { repository.save(novel); }
}
