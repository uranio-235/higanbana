package local.higanbana.infrastructure.ai;

import java.util.Optional;
import local.higanbana.domain.entity.AiResponseCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AiResponseCacheRepository extends JpaRepository<AiResponseCacheEntry, String> {
    @Query("""
        SELECT entry.response FROM AiResponseCacheEntry entry
        WHERE entry.cacheKey = :key AND entry.expiresAt > CURRENT_TIMESTAMP
        """)
    Optional<String> findActiveResponse(@Param("key") String key);

    @Modifying
    @Query("DELETE FROM AiResponseCacheEntry entry WHERE entry.cacheKey = :key")
    int deleteByCacheKey(@Param("key") String key);

    @Modifying
    @Query("DELETE FROM AiResponseCacheEntry entry WHERE entry.expiresAt <= CURRENT_TIMESTAMP")
    int deleteExpired();
}
