package docprocessor.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import docprocessor.job.JobServiceApplication;
import docprocessor.processor.ProcessorApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.client.RestClient;

/** Runs both services on free ports and drives the public REST API. */
class EndToEndTest {

    private ConfigurableApplicationContext jobService;
    private ConfigurableApplicationContext processor;

    @AfterEach
    void stopServices() {
        if (processor != null) {
            processor.close();
        }
        if (jobService != null) {
            jobService.close();
        }
    }

    @Test
    void submitJob_bothServicesUp_completesWithResults() {
        int jobPort = TestSocketUtils.findAvailableTcpPort();
        int processorPort = TestSocketUtils.findAvailableTcpPort();
        startServices(jobPort, processorPort, "ws://localhost:" + jobPort + "/ws/events");

        Map<String, Object> job = awaitCompleted(jobPort, submit(jobPort));

        List<Map<String, Object>> results = results(job);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).get("documentId")).isEqualTo("d1");
        assertThat(results.get(0).get("wordCount")).isEqualTo(4);
    }

    @Test
    void submitJob_eventsNeverArrive_pollerCompletesJob() {
        int jobPort = TestSocketUtils.findAvailableTcpPort();
        int processorPort = TestSocketUtils.findAvailableTcpPort();
        int closedPort = TestSocketUtils.findAvailableTcpPort();
        startServices(jobPort, processorPort, "ws://localhost:" + closedPort + "/ws/events",
                "job.poller.interval=200ms", "job.poller.stale-after=200ms");

        Map<String, Object> job = awaitCompleted(jobPort, submit(jobPort));

        assertThat(results(job)).hasSize(3);
    }

    /** Starts Job Service first, then Processor; extra properties go to the Job Service. */
    private void startServices(int jobPort, int processorPort, String wsUrl, String... jobExtras) {
        List<String> jobArgs = new ArrayList<>(List.of(
                "--server.port=" + jobPort,
                "--job.processor.base-url=http://localhost:" + processorPort));
        for (String extra : jobExtras) {
            jobArgs.add("--" + extra);
        }
        jobService = new SpringApplicationBuilder(JobServiceApplication.class)
                .run(jobArgs.toArray(String[]::new));
        processor = new SpringApplicationBuilder(ProcessorApplication.class)
                .run("--server.port=" + processorPort, "--processor.job-service-ws-url=" + wsUrl);
    }

    private String submit(int jobPort) {
        // A String body gets a Content-Length header, which the Job Service requires.
        String body = """
                {"documents":[
                  {"id":"d1","text":"alpha beta gamma delta"},
                  {"id":"d2","text":"one two three"},
                  {"id":"d3","text":"hello world"}]}""";
        Map<String, Object> created = client(jobPort).post().uri("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(new MapType());
        return String.valueOf(created.get("id"));
    }

    private Map<String, Object> awaitCompleted(int jobPort, String id) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(get(jobPort, id).get("status")).isEqualTo("COMPLETED"));
        return get(jobPort, id);
    }

    private Map<String, Object> get(int jobPort, String id) {
        return client(jobPort).get().uri("/api/jobs/{id}", id).retrieve().body(new MapType());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> results(Map<String, Object> job) {
        return (List<Map<String, Object>>) job.get("results");
    }

    private static RestClient client(int port) {
        return RestClient.create("http://localhost:" + port);
    }

    /** Type token for reading a JSON object into a map. */
    private static class MapType extends org.springframework.core.ParameterizedTypeReference<Map<String, Object>> {
    }
}
