package local.higanbana.domain;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Chunking {
    private Chunking() {}

    public static List<List<String>> analysisWindows(String source, int limit) {
        List<List<String>> windows = new ArrayList<>();
        for (String block : split(source, Math.min(limit, 5000))) {
            BreakIterator sentences = BreakIterator.getSentenceInstance(Locale.forLanguageTag("es"));
            sentences.setText(block);
            List<String> units = new ArrayList<>();
            int start = sentences.first();
            for (int end = sentences.next(); end != BreakIterator.DONE; start = end, end = sentences.next()) {
                for (String paragraph : block.substring(start, end).split("(?<=\\n)")) {
                    if (!paragraph.isEmpty()) { units.add(paragraph); }
                }
            }
            for (int index = 0; index < units.size(); index += 40) {
                windows.add(List.copyOf(units.subList(index, Math.min(index + 40, units.size()))));
            }
        }
        return List.copyOf(windows);
    }

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