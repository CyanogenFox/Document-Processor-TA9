package docprocessor.processor.analysis;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.WordFrequency;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Computes word statistics, character count and SHA-256 for one document. */
public class DocumentAnalyzer {

    private static final int TOP_WORDS_LIMIT = 5;

    // A word starts with a letter, may continue with letters, marks (accents) and digits,
    // and may contain inner apostrophes (straight or curly) followed by a letter or digit.
    private static final Pattern WORD = Pattern.compile(
            "\\p{L}[\\p{L}\\p{M}\\p{N}]*(?:['\\u2019][\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N}]*)*");

    public DocumentResult analyze(DocumentInput document) {
        long start = System.nanoTime();
        String text = document.text();

        Map<String, Integer> counts = new HashMap<>();
        int wordCount = 0;
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().replace('\u2019', '\'').toLowerCase(Locale.ROOT);
            counts.merge(word, 1, Integer::sum);
            wordCount++;
        }

        List<WordFrequency> topWords = counts.entrySet().stream()
                .map(e -> new WordFrequency(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(WordFrequency::count).reversed()
                        .thenComparing(WordFrequency::word))
                .limit(TOP_WORDS_LIMIT)
                .toList();

        int characterCount = text.codePointCount(0, text.length());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        return new DocumentResult(document.id(), wordCount, counts.size(), topWords,
                sha256(text), characterCount, elapsedMs);
    }

    // SHA-256 of the UTF-8 bytes as lowercase hex.
    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every JVM", e);
        }
    }
}
