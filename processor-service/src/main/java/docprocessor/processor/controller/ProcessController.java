package docprocessor.processor.controller;

import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessResponse;
import docprocessor.processor.service.ProcessingService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Internal REST API of the Processor Service, called only by the Job Service. */
@RestController
@RequestMapping("/api/process")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessingService service;

    // 202 when the job was newly accepted, 200 with the current state when the jobId is already known.
    @PostMapping
    public ResponseEntity<ProcessResponse> submit(@Valid @RequestBody ProcessRequest request) {
        ProcessingService.Submission submission = service.submit(request);
        HttpStatus status = submission.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(submission.job().toResponse());
    }

    @GetMapping("/{jobId}")
    public ProcessResponse get(@PathVariable UUID jobId) {
        return service.get(jobId).toResponse();
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID jobId) {
        service.delete(jobId);
    }
}
