package local.higanbana.infrastructure.memory;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import local.higanbana.domain.memory.TextEmbedder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalOnnxTextEmbedder implements TextEmbedder {
    private static final int MAX_TOKENS = 128;
    private final String onnxPath;
    private final String tokenizerPath;
    private volatile RuntimeModel runtime;

    public LocalOnnxTextEmbedder(@Value("${app.memory.onnx-path:}") String onnxPath,
                                 @Value("${app.memory.tokenizer-path:}") String tokenizerPath) {
        this.onnxPath = onnxPath;
        this.tokenizerPath = tokenizerPath;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto para generar embedding es obligatorio.");
        }
        return model().embed(text);
    }

    private RuntimeModel model() {
        RuntimeModel current = runtime;
        if (current != null) { return current; }
        synchronized (this) {
            if (runtime == null) { runtime = loadModel(); }
            return runtime;
        }
    }

    private RuntimeModel loadModel() {
        if (onnxPath.isBlank() || tokenizerPath.isBlank()) {
            throw new IllegalStateException("Configura HIGANBANA_MEMORY_ONNX y HIGANBANA_MEMORY_TOKENIZER para usar memoria RAG.");
        }
        Path model = Path.of(onnxPath);
        Path tokenizerFile = Path.of(tokenizerPath);
        if (!Files.isRegularFile(model) || !Files.isRegularFile(tokenizerFile)) {
            throw new IllegalStateException("No se encontraron el modelo ONNX o tokenizer.json configurados para memoria RAG.");
        }
        try {
            var tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile,
                    Map.of("maxLength", String.valueOf(MAX_TOKENS), "truncation", "true"));
            var environment = OrtEnvironment.getEnvironment();
            var options = new OrtSession.SessionOptions();
            try {
                return new RuntimeModel(environment, environment.createSession(model.toString(), options), tokenizer);
            } finally {
                options.close();
            }
        } catch (IOException | ai.onnxruntime.OrtException exception) {
            throw new IllegalStateException("No se pudo cargar el modelo local de embeddings.", exception);
        }
    }

    @PreDestroy
    void close() {
        RuntimeModel current = runtime;
        if (current != null) {
            current.close();
            runtime = null;
        }
    }

    private static final class RuntimeModel {
        private final OrtEnvironment environment;
        private final OrtSession session;
        private final HuggingFaceTokenizer tokenizer;

        private RuntimeModel(OrtEnvironment environment, OrtSession session, HuggingFaceTokenizer tokenizer) {
            this.environment = environment;
            this.session = session;
            this.tokenizer = tokenizer;
        }

        private synchronized float[] embed(String text) {
            Encoding encoding = tokenizer.encode(text);
            long[] ids = encoding.getIds();
            long[] mask = encoding.getAttentionMask();
            long[] typeIds = encoding.getTypeIds();
            Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
            try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, new long[][] { ids });
                 OnnxTensor maskTensor = OnnxTensor.createTensor(environment, new long[][] { mask });
                 OnnxTensor typesTensor = OnnxTensor.createTensor(environment, new long[][] { typeIds });) {
                for (String input : session.getInputNames()) {
                    OnnxTensor tensor = switch (input) {
                        case "input_ids" -> idsTensor;
                        case "attention_mask" -> maskTensor;
                        case "token_type_ids" -> typesTensor;
                        default -> throw new IllegalStateException("Entrada ONNX no compatible: " + input);
                    };
                    tensors.put(input, tensor);
                }
                try (OrtSession.Result output = session.run(tensors)) {
                    Object values = output.get(0).getValue();
                    if (!(values instanceof float[][][] tokenEmbeddings) || tokenEmbeddings.length != 1) {
                        throw new IllegalStateException("El modelo ONNX debe devolver embeddings de tokens [1, secuencia, dimension].");
                    }
                    return meanPool(tokenEmbeddings[0], mask);
                }
            } catch (ai.onnxruntime.OrtException exception) {
                throw new IllegalStateException("Falló la inferencia local de embeddings.", exception);
            }
        }

        private float[] meanPool(float[][] tokens, long[] attentionMask) {
            if (tokens.length != attentionMask.length || tokens.length == 0) {
                throw new IllegalStateException("El modelo ONNX devolvió una secuencia incompatible con el tokenizer.");
            }
            int dimensions = tokens[0].length;
            float[] embedding = new float[dimensions];
            int included = 0;
            for (int token = 0; token < tokens.length; token++) {
                if (attentionMask[token] == 0) { continue; }
                if (tokens[token].length != dimensions) {
                    throw new IllegalStateException("El modelo ONNX devolvió dimensiones de embedding inconsistentes.");
                }
                for (int dimension = 0; dimension < dimensions; dimension++) {
                    embedding[dimension] += tokens[token][dimension];
                }
                included++;
            }
            if (included == 0) { throw new IllegalStateException("El tokenizer no produjo tokens útiles."); }
            double normSquared = 0;
            for (int dimension = 0; dimension < dimensions; dimension++) {
                embedding[dimension] /= included;
                normSquared += (double) embedding[dimension] * embedding[dimension];
            }
            if (normSquared == 0 || !Double.isFinite(normSquared)) {
                throw new IllegalStateException("El modelo ONNX devolvió un embedding inválido.");
            }
            float norm = (float) Math.sqrt(normSquared);
            for (int dimension = 0; dimension < dimensions; dimension++) {
                embedding[dimension] /= norm;
            }
            return embedding;
        }

        private void close() {
            tokenizer.close();
            try {
                session.close();
            } catch (ai.onnxruntime.OrtException exception) {
                throw new IllegalStateException("No se pudo cerrar el modelo local de embeddings.", exception);
            }
        }
    }
}