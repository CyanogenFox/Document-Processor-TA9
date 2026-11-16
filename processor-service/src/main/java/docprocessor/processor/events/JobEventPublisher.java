package docprocessor.processor.events;

import docprocessor.contracts.JobEvent;

/** Receives job lifecycle events from the worker. */
public interface JobEventPublisher {

    void publish(JobEvent event);
}
