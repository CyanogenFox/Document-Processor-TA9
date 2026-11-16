package docprocessor.contracts;

import jakarta.validation.constraints.NotBlank;

/** One document to process. */
public record DocumentInput(@NotBlank String id, @NotBlank String text) {
}
