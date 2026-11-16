package docprocessor.processor.controller;

import docprocessor.processor.service.ProcessJobActiveException;
import docprocessor.processor.service.ProcessJobNotFoundException;
import docprocessor.processor.service.QueueFullException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns service exceptions into RFC 9457 problem responses; validation errors come from the base class. */
@Slf4j
@RestControllerAdvice
public class ProcessorExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProcessJobNotFoundException.class)
    ProblemDetail notFound(ProcessJobNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProcessJobActiveException.class)
    ProblemDetail conflict(ProcessJobActiveException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // Retry-After tells the Job Service to try again shortly.
    @ExceptionHandler(QueueFullException.class)
    ResponseEntity<ProblemDetail> queueFull(QueueFullException e) {
        log.warn("queue full: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()));
    }

    // Last resort: log the cause, but never expose it to the caller.
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
