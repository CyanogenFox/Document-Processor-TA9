package docprocessor.processor.job;

/** Thrown when all workers are busy and the waiting queue is full. */
public class QueueFullException extends RuntimeException {

    public QueueFullException(String message) {
        super(message);
    }
}
