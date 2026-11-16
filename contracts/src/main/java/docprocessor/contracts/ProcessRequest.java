package docprocessor.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request to process a batch of documents as one job. */
public record ProcessRequest(
        @NotNull UUID jobId,
        @NotNull @Size(min = 1, max = 1000) List<@Valid DocumentInput> documents) {
}
