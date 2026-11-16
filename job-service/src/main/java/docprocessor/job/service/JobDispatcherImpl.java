package docprocessor.job.service;

import docprocessor.contracts.ProcessRequest;
import docprocessor.job.config.JobProperties;
import docprocessor.job.domain.FailureReason;
import docprocessor.job.domain.Job;
import docprocessor.job.processor.ProcessorClient;
import docprocessor.job.processor.ProcessorRejectedException;
import docprocessor.job.processor.ProcessorUnavailableException;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/** Sends each job to the Processor on a background thread, a limited number at a time, and records the outcome. */
@Slf4j
@Service
public class JobDispatcherImpl implements JobDispatcher {

    private final JobLifecycle lifecycle;
    private final ProcessorClient client;
    private final TaskExecutor executor;
    private final Semaphore permits;

    public JobDispatcherImpl(JobLifecycle lifecycle, ProcessorClient client, JobProperties properties,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.lifecycle = lifecycle;
        this.client = client;
        this.executor = executor;
        this.permits = new Semaphore(properties.processor().maxConcurrentDispatches());
    }

    @Override
    public void dispatch(Job job) {
        executor.execute(() -> send(job));
    }

    /** Holds a permit across all retries; every path ends with the job accepted or failed. */
    private void send(Job job) {
        MDC.put("jobId", job.id().toString());
        try {
            permits.acquire();
            try {
                client.submit(new ProcessRequest(job.id(), job.documents()));
                lifecycle.accepted(job.id());
                log.info("Job {} dispatched", job.id());
            } finally {
                permits.release();
            }
        } catch (ProcessorUnavailableException e) {
            log.warn("Job {} failed: Processor unavailable", job.id());
            lifecycle.failed(job.id(), FailureReason.PROCESSOR_UNAVAILABLE);
        } catch (ProcessorRejectedException e) {
            log.warn("Job {} failed: Processor rejected it", job.id());
            lifecycle.failed(job.id(), FailureReason.PROCESSOR_REJECTED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Job {} dispatch interrupted", job.id());
            lifecycle.failed(job.id(), FailureReason.INTERNAL_ERROR);
        } catch (Exception e) {
            log.error("Job {} failed unexpectedly", job.id(), e);
            lifecycle.failed(job.id(), FailureReason.INTERNAL_ERROR);
        } finally {
            MDC.remove("jobId");
        }
    }
}
