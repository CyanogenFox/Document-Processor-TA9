package docprocessor.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Checks that the Job Service context loads and reports healthy. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "job.poller.enabled=false")
@AutoConfigureRestTestClient
class JobServiceApplicationTest {

    @Autowired
    private RestTestClient client;

    @Test
    void health_contextLoaded_returnsUp() {
        String body = client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        assertThat(body).contains("\"status\":\"UP\"");
    }
}
