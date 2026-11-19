package local.higanbana.application;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import local.higanbana.domain.memory.MemoryMatch;
import local.higanbana.domain.memory.MemoryRepository;
import local.higanbana.domain.memory.TextEmbedder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemoryService {
    public static final int EMBEDDING_DIMENSIONS = 384;
    private static final int MAX_RESULTS = 20;
    private final TextEmbedder embedder;
    private final MemoryRepository repository;

    public MemoryService(TextEmbedder embedder, MemoryRepository repository) {
        this.embedder = embedder;
        this.repository = repository;
    }

    public void remember(String scope, String content) {
        requireScope(scope);
        requireText(content);
        String hash = contentHash(content);
        if (!repository.contains(scope, hash)) {
            repository.remember(scope, content, hash, validEmbedding(embedder.embed(content)));
        }
    }

    public List<MemoryMatch> search(String scope, String query, int limit) {
        requireScope(scope);
        requireText(query);
        if (limit < 1 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException("El limite de recuerdos debe estar entre 1 y " + MAX_RESULTS + ".");
        }
        return List.copyOf(repository.search(scope, validEmbedding(embedder.embed(query)), limit));
    }

    private float[] validEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("El modelo de memoria debe producir embeddings de 384 dimensiones.");
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("El modelo de memoria produjo un embedding no finito.");
            }
        }
        return embedding.clone();
    }

    private void requireScope(String scope) {
        if (scope == null || scope.isBlank() || scope.length() > 255) {
            throw new IllegalArgumentException("El alcance de memoria es obligatorio y no puede superar 255 caracteres.");
        }
    }

    private void requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto de memoria es obligatorio.");
        }
    }

    private String contentHash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible.", exception);
        }
    }
}