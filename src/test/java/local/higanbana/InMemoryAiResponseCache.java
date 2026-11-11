package local.higanbana;

import local.higanbana.domain.ai.AiResponseCache;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryAiResponseCache implements AiResponseCache {
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<String> find(String key) {
        return Optional.ofNullable(entries.get(key))
                .filter(entry -> entry.expiresAt().isAfter(Instant.now()))
                .map(Entry::response);
    }

    @Override
    public void evict(String key) {
        entries.remove(key);
    }

    @Override
    public void store(String key, String operation, String provider, String model, String promptVersion,
                      String response, Duration ttl) {
        if (!ttl.isNegative() && !ttl.isZero()) {
            entries.put(key, new Entry(response, Instant.now().plus(ttl)));
        }
    }

    private record Entry(String response, Instant expiresAt) {}
}
