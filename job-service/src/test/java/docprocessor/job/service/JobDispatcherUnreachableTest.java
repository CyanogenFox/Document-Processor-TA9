package docprocessor.job.service;

import static org.assertj.core.api.Assertions.assertThat;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.config.JobProperties;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.processor.ProcessorClient;
import docprocessor.job.repository.JobRepository;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {"job.processor.retry-delay-ms=10", "job.poller.enabled=false"})
class JobDispatcherUnreachableTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        registry.add("job.processor.base-url", () -> "http://localhost:" + closedPort);
    }

    @Autowired
    private JobDispatcher dispatcher;
    @Autowired
    private JobRepository repository;

    @Autowired
    private JobLifecycle lifecycle;
    @Autowired
    private ProcessorClient client;
    @Autowired
    private JobProperties properties;

    @Test
    void dispatch_processorUnreachable_jobStaysPending() {
        Instant now = Instant.now();
        Job job = Job.builder().id(UUID.randomUUID()).status(JobStatus.PENDING)
                .documents(List.of(new DocumentInput("a", "hello"))).documentCount(1)
                .createdAt(now).updatedAt(now).build();
        repository.save(job);
        JobDispatcher syncDispatcher = new JobDispatcherImpl(lifecycle, client, properties, new SyncTaskExecutor());

        syncDispatcher.dispatch(job);

        Job stored = repository.findById(job.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(JobStatus.PENDING);
        assertThat(stored.dispatchedAt()).isNull();
        assertThat(stored.documents()).isNotNull();
    }
}
