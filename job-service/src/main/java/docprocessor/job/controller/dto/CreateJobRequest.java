package docprocessor.job.controller.dto;

import docprocessor.contracts.DocumentInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Body of POST /api/jobs. */
public record CreateJobRequest(@NotNull @Size(min = 1, max = 1000) List<@NotNull @Valid DocumentInput> documents) {
}
