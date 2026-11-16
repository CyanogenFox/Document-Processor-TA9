package docprocessor.contracts;

import java.util.List;

/** Analysis result for a single document. */
public record DocumentResult(
        String documentId,
        int wordCount,
        int uniqueWordCount,
        List<WordFrequency> topWords,
        String sha256,
        int characterCount,
        long processingTimeMs) {
}
