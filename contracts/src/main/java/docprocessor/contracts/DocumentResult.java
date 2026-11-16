package docprocessor.contracts;

import java.util.List;

/** Analysis result for a single document; error is null on success. */
public record DocumentResult(
        String documentId,
        int wordCount,
        int uniqueWordCount,
        List<WordFrequency> topWords,
        String sha256,
        int characterCount,
        long processingTimeMs,
        String error) {

    // Result for a document that could not be analysed: zero counts, no hash, only the error code.
    public static DocumentResult failed(String documentId, String error) {
        return new DocumentResult(documentId, 0, 0, null, null, 0, 0L, error);
    }
}
