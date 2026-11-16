package docprocessor.processor.job;

/** Thrown when no job exists for the given id. */
public class ProcessJobNotFoundException extends RuntimeException {

    public ProcessJobNotFoundException(String message) {
        super(message);
    }
}
