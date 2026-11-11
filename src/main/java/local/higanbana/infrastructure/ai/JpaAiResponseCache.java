package local.higanbana.infrastructure.ai;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import local.higanbana.domain.ai.AiResponseCache;
import local.higanbana.domain.entity.AiResponseCacheEntry;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAiResponseCache implements AiResponseCache {
    private final AiResponseCacheRepository repository;

    public JpaAiResponseCache(AiResponseCacheRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        return repository.findActiveResponse(key);
    }

    @Override
    @Transactional
    public void evict(String key) {
        repository.deleteByCacheKey(key);
    }

    @Override
    @Transactional
    public synchronized void store(String key, String operation, String provider, String model, String promptVersion,
                                  String response, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        repository.deleteExpired();
        LocalDateTime createdAt = LocalDateTime.now();
        repository.save(new AiResponseCacheEntry(key, operation, provider, model, promptVersion, response,
                createdAt, createdAt.plus(ttl)));
    }
}
