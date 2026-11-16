package docprocessor.job.service;

/** Thrown when a job that is still running is asked to be deleted. */
public class JobActiveException extends RuntimeException {

    public JobActiveException(String message) {
        super(message);
    }
}
