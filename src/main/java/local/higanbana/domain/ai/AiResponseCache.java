package local.higanbana.domain.ai;

import java.time.Duration;
import java.util.Optional;

public interface AiResponseCache {
    Optional<String> find(String key);

    void store(String key, String operation, String provider, String model, String promptVersion,
               String response, Duration ttl);
}