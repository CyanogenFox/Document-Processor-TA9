package docprocessor.job.service;

import docprocessor.job.domain.Job;

/** Hands a stored job over to the Processor Service. */
public interface JobDispatcher {

    void dispatch(Job job);
}
