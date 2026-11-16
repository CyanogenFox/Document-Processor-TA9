package docprocessor.processor.service;

/** Thrown when a job that is still queued or processing is deleted. */
public class ProcessJobActiveException extends RuntimeException {

    public ProcessJobActiveException(String message) {
        super(message);
    }
}
