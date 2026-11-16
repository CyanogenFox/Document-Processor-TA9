package docprocessor.job.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.controller.dto.CreateJobRequest;
import docprocessor.job.controller.dto.JobResponse;
import docprocessor.job.controller.dto.JobSummary;
import docprocessor.job.controller.dto.PageResponse;
import docprocessor.job.domain.Job;
import docprocessor.job.domain.JobStatus;
import docprocessor.job.repository.JobRepository;
import docprocessor.job.service.JobDispatcher;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/** HTTP-level tests of the Job REST API against a real server, so Content-Length is real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"job.max-request-size=1KB", "job.poller.enabled=false"})
@AutoConfigureRestTestClient
class JobControllerTest {

    @Autowired
    private RestTestClient client;
    @Autowired
    private JobRepository repository;
    @LocalServerPort
    private int port;
    @MockitoBean
    private JobDispatcher dispatcher;

    private CreateJobRequest request(String... ids) {
        return new CreateJobRequest(Arrays.stream(ids).map(id -> new DocumentInput(id, "the cat")).toList());
    }

    private RestTestClient.ResponseSpec post(Object body) {
        return client.post().uri("/api/jobs").contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    private Job save(JobStatus status, Instant createdAt) {
        Job job = Job.builder().id(UUID.randomUUID()).status(status).documentCount(1)
                .createdAt(createdAt).updatedAt(createdAt).build();
        repository.save(job);
        return job;
    }

    private PageResponse<JobSummary> list(String query) {
        return client.get().uri("/api/jobs" + query).exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<PageResponse<JobSummary>>() { }).getResponseBody();
    }

    @Test
    void post_validJob_returns202WithLocationAndPending() {
        JobResponse body = post(request("a", "b")).expectStatus().isAccepted()
                .expectHeader().value("Location", loc -> assertThat(loc).matches("/api/jobs/[0-9a-f-]{36}"))
                .returnResult(JobResponse.class).getResponseBody();

        assertThat(body.status()).isEqualTo(JobStatus.PENDING);
        assertThat(body.documentCount()).isEqualTo(2);
        verify(dispatcher).dispatch(any(Job.class));
        client.get().uri("/api/jobs/{id}", body.id()).exchange().expectStatus().isOk();
    }

    @Test
    void post_emptyDocuments_returns400() {
        post("{\"documents\":[]}").expectStatus().isBadRequest();
    }

    @Test
    void post_missingDocuments_returns400() {
        post("{}").expectStatus().isBadRequest();
    }

    @Test
    void post_blankText_returns400() {
        post("{\"documents\":[{\"id\":\"a\",\"text\":\" \"}]}").expectStatus().isBadRequest();
    }

    @Test
    void post_nullDocumentElement_returns400() {
        post("{\"documents\":[null]}").expectStatus().isBadRequest();
    }

    @Test
    void post_duplicateDocumentIds_returns400() {
        post(request("a", "a")).expectStatus().isBadRequest();
    }

    @Test
    void post_malformedJson_returns400() {
        post("{not json").expectStatus().isBadRequest();
    }

    @Test
    void post_bodyOverLimit_returns413() {
        String big = "x".repeat(2000);

        post("{\"documents\":[{\"id\":\"a\",\"text\":\"" + big + "\"}]}").expectStatus().isEqualTo(413)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(413);
    }

    @Test
    void post_noContentLength_returns411() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/jobs"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofInputStream(() -> new ByteArrayInputStream("{}".getBytes()))).build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(req, BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(411);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(JsonMapper.builder().build().readTree(response.body()).get("status").asInt()).isEqualTo(411);
    }

    @Test
    void get_unknownJob_returns404() {
        client.get().uri("/api/jobs/{id}", UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }

    @Test
    void get_notAUuid_returns400() {
        client.get().uri("/api/jobs/nope").exchange().expectStatus().isBadRequest();
    }

    @Test
    void list_returnsSummariesNewestFirst() {
        Job older = save(JobStatus.COMPLETED, Instant.parse("2020-01-01T00:00:00Z"));
        Job newer = save(JobStatus.COMPLETED, Instant.parse("2020-01-02T00:00:00Z"));

        String raw = client.get().uri("/api/jobs?status=COMPLETED&size=100").exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody();
        List<UUID> ids = list("?status=COMPLETED&size=100").content().stream().map(JobSummary::id).toList();

        assertThat(raw).doesNotContain("\"results\"");
        assertThat(ids.indexOf(newer.id())).isLessThan(ids.indexOf(older.id()));
    }

    @Test
    void list_statusFilterAndPaging() {
        for (int i = 0; i < 3; i++) {
            save(JobStatus.FAILED, Instant.parse("2021-01-0" + (i + 1) + "T00:00:00Z"));
        }

        PageResponse<JobSummary> page = list("?status=FAILED&page=1&size=2");

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.content()).isNotEmpty().allMatch(s -> s.status() == JobStatus.FAILED);
        assertThat(page.totalPages()).isEqualTo((int) Math.ceil(page.totalElements() / 2.0));
    }

    @Test
    void list_sizeOver100_cappedTo100() {
        assertThat(list("?size=500").size()).isEqualTo(100);
    }

    @Test
    void list_negativePage_returns400() {
        client.get().uri("/api/jobs?page=-1").exchange().expectStatus().isBadRequest();
    }

    @Test
    void list_sizeBelowOne_returns400() {
        client.get().uri("/api/jobs?size=0").exchange().expectStatus().isBadRequest();
    }

    @Test
    void list_unknownStatus_returns400() {
        client.get().uri("/api/jobs?status=BOGUS").exchange().expectStatus().isBadRequest();
    }

    @Test
    void delete_pendingJob_returns409() {
        Job job = save(JobStatus.PENDING, Instant.now());

        client.delete().uri("/api/jobs/{id}", job.id()).exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void delete_finishedJob_returns204ThenGet404() {
        Job job = save(JobStatus.COMPLETED, Instant.now());

        client.delete().uri("/api/jobs/{id}", job.id()).exchange().expectStatus().isNoContent();

        client.get().uri("/api/jobs/{id}", job.id()).exchange().expectStatus().isNotFound();
    }

    @Test
    void delete_unknownJob_returns404() {
        client.delete().uri("/api/jobs/{id}", UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }
}
