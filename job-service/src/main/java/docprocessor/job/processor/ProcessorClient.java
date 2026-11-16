package docprocessor.job.processor;

import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessResponse;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** HTTP client for the Processor Service; failures become ProcessorUnavailableException or ProcessorRejectedException. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessorClient {

    private static final String PATH = "/api/process";

    private final RestClient restClient;

    /** Submits a job; retried on ProcessorUnavailableException (each retry re-enters and logs its own outcome). */
    @Retryable(
            includes = ProcessorUnavailableException.class,
            maxRetriesString = "${job.processor.max-retries:3}",
            delayString = "${job.processor.retry-delay-ms:300}",
            multiplier = 2)
    public ProcessResponse submit(ProcessRequest request) {
        try {
            ProcessResponse response = restClient.post().uri(PATH).body(request).retrieve().body(ProcessResponse.class);
            log.info("Processor accepted job {}", request.jobId());
            return response;
        } catch (RuntimeException e) {
            log.warn("Submit of job {} failed: {}", request.jobId(), e.toString());
            throw map(e);
        }
    }

    /** Reads the Processor view of a job; empty if it does not know the job. Not retried. */
    public Optional<ProcessResponse> find(UUID jobId) {
        try {
            return Optional.ofNullable(restClient.get().uri(PATH + "/{id}", jobId).retrieve().body(ProcessResponse.class));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw map(e);
        } catch (ResourceAccessException e) {
            throw map(e);
        }
    }

    /** Asks the Processor to drop a job; a 404 is ignored. Not retried. */
    public void delete(UUID jobId) {
        try {
            restClient.delete().uri(PATH + "/{id}", jobId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw map(e);
            }
        } catch (ResourceAccessException e) {
            throw map(e);
        }
    }

    /** I/O errors and 5xx become unavailable, 4xx become rejected; anything else is returned unchanged. */
    private RuntimeException map(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return new ProcessorUnavailableException("Processor unreachable: " + e.getMessage(), e);
        }
        if (e instanceof RestClientResponseException response) {
            if (response.getStatusCode().is5xxServerError()) {
                return new ProcessorUnavailableException("Processor answered " + response.getStatusCode(), e);
            }
            log.warn("Processor rejected the request with status {}", response.getStatusCode().value());
            return new ProcessorRejectedException("Processor answered " + response.getStatusCode(), e);
        }
        return e;
    }
}
