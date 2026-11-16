package docprocessor.processor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import docprocessor.contracts.DocumentInput;
import docprocessor.contracts.ProcessRequest;
import docprocessor.contracts.ProcessResponse;
import docprocessor.contracts.ProcessStatus;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** HTTP-level tests of the Processor REST API against the real service and workers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProcessControllerTest {

    @Autowired
    private RestTestClient client;

    private ProcessRequest request(UUID jobId) {
        return new ProcessRequest(jobId, List.of(new DocumentInput("d1", "the cat and the hat")));
    }

    private RestTestClient.ResponseSpec post(Object body) {
        return client.post().uri("/api/process").contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private ProcessResponse getJob(UUID jobId) {
        return client.get().uri("/api/process/{id}", jobId).exchange()
                .expectStatus().isOk()
                .returnResult(ProcessResponse.class).getResponseBody();
    }

    @Test
    void post_validJob_returns202ThenGetShowsCompleted() {
        UUID jobId = UUID.randomUUID();

        post(request(jobId)).expectStatus().isAccepted();

        await().untilAsserted(() -> assertThat(getJob(jobId).status()).isEqualTo(ProcessStatus.COMPLETED));
        assertThat(getJob(jobId).results()).hasSize(1);
    }

    @Test
    void post_sameJobIdAgain_returns200() {
        UUID jobId = UUID.randomUUID();
        post(request(jobId)).expectStatus().isAccepted();

        post(request(jobId)).expectStatus().isOk();
    }

    @Test
    void post_noDocuments_returns400ProblemDetail() {
        post(new ProcessRequest(UUID.randomUUID(), List.of()))
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void post_blankText_returns400() {
        post(new ProcessRequest(UUID.randomUUID(), List.of(new DocumentInput("d1", " "))))
                .expectStatus().isBadRequest();
    }

    @Test
    void post_1001Documents_returns400() {
        List<DocumentInput> documents = IntStream.range(0, 1001)
                .mapToObj(i -> new DocumentInput("d" + i, "text")).toList();

        post(new ProcessRequest(UUID.randomUUID(), documents)).expectStatus().isBadRequest();
    }

    @Test
    void get_unknownJob_returns404() {
        client.get().uri("/api/process/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void delete_finishedJob_returns204ThenGet404() {
        UUID jobId = UUID.randomUUID();
        post(request(jobId)).expectStatus().isAccepted();
        await().untilAsserted(() -> assertThat(getJob(jobId).status()).isEqualTo(ProcessStatus.COMPLETED));

        client.delete().uri("/api/process/{id}", jobId).exchange().expectStatus().isNoContent();

        client.get().uri("/api/process/{id}", jobId).exchange().expectStatus().isNotFound();
    }

    @Test
    void delete_unknownJob_returns404() {
        client.delete().uri("/api/process/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound();
    }
}
