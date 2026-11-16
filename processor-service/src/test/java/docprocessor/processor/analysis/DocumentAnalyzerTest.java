package docprocessor.processor.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.DocumentResult;
import docprocessor.contracts.WordFrequency;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for DocumentAnalyzer (plain JUnit, no Spring). */
class DocumentAnalyzerTest {

    private final DocumentAnalyzer analyzer = new DocumentAnalyzer();

    private DocumentResult analyze(String text) {
        return analyzer.analyze(new DocumentInput("d1", text));
    }

    @Test
    void analyze_repeatedWordsWithPunctuation_countsCaseInsensitively() {
        DocumentResult result = analyze("Hello, hello world!");

        assertThat(result.wordCount()).isEqualTo(3);
        assertThat(result.uniqueWordCount()).isEqualTo(2);
        assertThat(result.topWords())
                .containsExactly(new WordFrequency("hello", 2), new WordFrequency("world", 1));
        assertThat(result.characterCount()).isEqualTo(19);
    }

    @Test
    void analyze_innerApostrophe_keepsContractionAsOneWord() {
        assertThat(analyze("Don't stop").wordCount()).isEqualTo(2);
    }

    @Test
    void analyze_surroundingQuotes_areNotPartOfWord() {
        DocumentResult result = analyze("'hello' hello");

        assertThat(result.wordCount()).isEqualTo(2);
        assertThat(result.uniqueWordCount()).isEqualTo(1);
    }

    @Test
    void analyze_curlyApostrophe_equalsStraightApostrophe() {
        DocumentResult result = analyze("Don\u2019t don't");

        assertThat(result.wordCount()).isEqualTo(2);
        assertThat(result.uniqueWordCount()).isEqualTo(1);
    }

    @Test
    void analyze_combiningAccent_staysInsideWord() {
        DocumentResult result = analyze("cafe\u0301 cafe\u0301");

        assertThat(result.wordCount()).isEqualTo(2);
        assertThat(result.uniqueWordCount()).isEqualTo(1);
    }

    @Test
    void analyze_hyphenatedAndNumbers_splitsHyphensAndIgnoresNumbers() {
        assertThat(analyze("state-of-the-art 2026").wordCount()).isEqualTo(4);
    }

    @Test
    void analyze_manyTies_returnsTopFiveAlphabeticalOnTies() {
        DocumentResult result = analyze("b a c b a c d e f");

        assertThat(result.topWords()).containsExactly(
                new WordFrequency("a", 2),
                new WordFrequency("b", 2),
                new WordFrequency("c", 2),
                new WordFrequency("d", 1),
                new WordFrequency("e", 1));
    }

    @Test
    void analyze_cyrillicText_lowercasesAndCounts() {
        DocumentResult result = analyze("\u041f\u0440\u0438\u0432\u0435\u0442 \u043c\u0438\u0440 \u043f\u0440\u0438\u0432\u0435\u0442");

        assertThat(result.wordCount()).isEqualTo(3);
        assertThat(result.uniqueWordCount()).isEqualTo(2);
        assertThat(result.topWords().get(0))
                .isEqualTo(new WordFrequency("\u043f\u0440\u0438\u0432\u0435\u0442", 2));
    }

    @Test
    void analyze_noWords_returnsZeroCountsAndEmptyTopWords() {
        DocumentResult result = analyze("!!! ... 123 \uD83D\uDE42");

        assertThat(result.wordCount()).isZero();
        assertThat(result.uniqueWordCount()).isZero();
        assertThat(result.topWords()).isEqualTo(List.of());
    }

    @Test
    void analyze_knownText_returnsKnownSha256() {
        assertThat(analyze("hello world").sha256())
                .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void analyze_emoji_countsCodePointsNotChars() {
        assertThat(analyze("\uD83D\uDE42").characterCount()).isEqualTo(1);
    }

    @Test
    void analyze_anyText_setsDocumentIdAndNonNegativeTime() {
        DocumentResult result = analyze("anything");

        assertThat(result.documentId()).isEqualTo("d1");
        assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
    }
}
