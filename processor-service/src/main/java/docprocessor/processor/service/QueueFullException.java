package docprocessor.processor.service;

/** Thrown when all workers are busy and the waiting queue is full. */
public class QueueFullException extends RuntimeException {

    public QueueFullException(String message) {
        super(message);
    }
}
