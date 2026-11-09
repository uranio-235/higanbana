package local.higanbana;

import java.util.ArrayList;
import java.util.List;

public final class Chunking {
    private Chunking() {}

    public static List<String> split(String source, int limit) {
        if (source == null || source.isBlank() || limit < 2 || limit > 8000) {
            throw new IllegalArgumentException("Texto obligatorio y limite de 2 a 8000 caracteres.");
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            int end = Math.min(start + limit, source.length());
            if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) {
                end--;
            }
            if (end < source.length()) {
                int boundary = source.lastIndexOf('\n', end - 1) + 1;
                if (boundary > start + limit / 2) {
                    end = boundary;
                }
            }
            result.add(source.substring(start, end));
            start = end;
        }
        return List.copyOf(result);
    }
}