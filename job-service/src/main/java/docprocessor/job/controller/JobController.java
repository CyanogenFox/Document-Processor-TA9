package docprocessor.job.controller;

import docprocessor.job.controller.dto.CreateJobRequest;
import docprocessor.job.controller.dto.JobResponse;
import docprocessor.job.controller.dto.JobSummary;
import docprocessor.job.controller.dto.PageResponse;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public REST API of the Job Service. */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService service;

    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        Job job = service.create(request.documents());
        return ResponseEntity.accepted().location(URI.create("/api/jobs/" + job.id())).body(JobResponse.from(job));
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(service.get(id));
    }

    @GetMapping
    public PageResponse<JobSummary> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return PageResponse.from(service.list(status, page, size), JobSummary::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
