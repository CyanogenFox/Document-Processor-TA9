package docprocessor.job.service;

/** Thrown when a job request breaks a rule that bean validation cannot express. */
public class InvalidJobException extends RuntimeException {

    public InvalidJobException(String message) {
        super(message);
    }
}
