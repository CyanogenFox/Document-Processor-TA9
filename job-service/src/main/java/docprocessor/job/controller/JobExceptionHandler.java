package docprocessor.job.controller;

import docprocessor.job.service.InvalidJobException;
import docprocessor.job.service.JobActiveException;
import docprocessor.job.service.JobNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns service exceptions into RFC 9457 problem responses; validation errors come from the base class. */
@Slf4j
@RestControllerAdvice
public class JobExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(JobNotFoundException.class)
    ProblemDetail notFound(JobNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(JobActiveException.class)
    ProblemDetail conflict(JobActiveException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidJobException.class)
    ProblemDetail invalid(InvalidJobException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Last resort: log the cause, but never expose it to the caller.
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
