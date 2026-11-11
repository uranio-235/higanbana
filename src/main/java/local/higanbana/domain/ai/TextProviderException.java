package local.higanbana.domain.ai;

public class TextProviderException extends IllegalArgumentException {
    public TextProviderException(String message) {
        super(message);
    }

    public TextProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}