package docprocessor.job.service;

/** Thrown when no job exists for the given id. */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String message) {
        super(message);
    }
}
