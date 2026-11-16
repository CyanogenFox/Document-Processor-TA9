package docprocessor.processor.job;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded pool of platform worker threads with a bounded waiting queue; rejects when full. */
public class JobQueue {

    private final ThreadPoolExecutor executor;

    public JobQueue(int workers, int queueCapacity) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = task -> new Thread(task, "job-worker-" + threadNumber.getAndIncrement());
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Runs the task on a worker, or throws QueueFullException when all workers and places are taken. */
    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            throw new QueueFullException("Job queue is full");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
