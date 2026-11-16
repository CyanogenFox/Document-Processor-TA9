package docprocessor.processor.web;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.ProcessRequest;
import docprocessor.processor.analysis.DocumentAnalyzer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Fills a one-worker, one-slot queue with a blocked analyzer to check the 503 response. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"processor.workers=1", "processor.queue-capacity=1"})
@AutoConfigureRestTestClient
class ProcessControllerQueueFullTest {

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Autowired
    private RestTestClient client;

    @MockitoBean
    private DocumentAnalyzer analyzer;

    @AfterEach
    void releaseWorker() {
        release.countDown();
    }

    private RestTestClient.ResponseSpec post() {
        ProcessRequest request = new ProcessRequest(UUID.randomUUID(), List.of(new DocumentInput("d1", "text")));
        return client.post().uri("/api/process").contentType(MediaType.APPLICATION_JSON).body(request).exchange();
    }

    @Test
    void post_queueFull_returns503WithRetryAfter() {
        when(analyzer.analyze(any())).thenAnswer(invocation -> {
            started.countDown();
            release.await();
            return null;
        });

        post().expectStatus().isAccepted(); // taken by the only worker, blocks in the analyzer
        await().until(() -> started.getCount() == 0); // worker has left the queue
        post().expectStatus().isAccepted(); // fills the one queue slot

        post().expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "1");
    }
}
