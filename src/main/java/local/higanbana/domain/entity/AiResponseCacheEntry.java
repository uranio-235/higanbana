package local.higanbana.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity(name = "AiResponseCacheEntry")
@Table(name = "ai_response_cache")
public class AiResponseCacheEntry {
    @Id
    @Column(name = "cache_key", nullable = false, length = 64)
    public String cacheKey;

    @Column(nullable = false, length = 80)
    public String operation;

    @Column(nullable = false)
    public String provider;

    @Column(nullable = false)
    public String model;

    @Column(name = "prompt_version", nullable = false, length = 80)
    public String promptVersion;

    @Column(nullable = false, columnDefinition = "text")
    public String response;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expiresAt;

    protected AiResponseCacheEntry() {}

    public AiResponseCacheEntry(String cacheKey, String operation, String provider, String model,
                                String promptVersion, String response, LocalDateTime createdAt,
                                LocalDateTime expiresAt) {
        this.cacheKey = cacheKey;
        this.operation = operation;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
        this.response = response;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
