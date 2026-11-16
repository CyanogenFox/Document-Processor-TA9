package docprocessor.job.processor;

/** The Processor could not be reached or answered with a 5xx; worth retrying. */
public class ProcessorUnavailableException extends RuntimeException {

    public ProcessorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
