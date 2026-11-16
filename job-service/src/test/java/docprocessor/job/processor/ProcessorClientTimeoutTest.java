package docprocessor.job.processor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.ProcessRequest;
import docprocessor.job.StubProcessor;
import docprocessor.job.StubProcessor.Reply;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Proves job.processor.read-timeout reaches the RestClient: a slow Processor reply ends as unavailable. */
@SpringBootTest(properties = {
        "job.processor.read-timeout=200ms",
        "job.processor.retry-delay-ms=10",
        "job.poller.enabled=false"})
class ProcessorClientTimeoutTest {

    private static final StubProcessor STUB = new StubProcessor();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("job.processor.base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Autowired
    private ProcessorClient processorClient;

    @Test
    void submit_replySlowerThanReadTimeout_throwsProcessorUnavailable() {
        STUB.script("POST", "/api/process", new Reply(202, "{\"status\":\"QUEUED\"}", 2000));
        ProcessRequest request = new ProcessRequest(UUID.randomUUID(), List.of(new DocumentInput("a", "hello")));

        assertThatThrownBy(() -> processorClient.submit(request)).isInstanceOf(ProcessorUnavailableException.class);
    }
}
