package docprocessor.job.controller;

import docprocessor.contracts.DocumentInput;
import docprocessor.job.controller.dto.CreateJobRequest;
import docprocessor.job.service.JobDispatcher;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Separate class because 1001 documents do not fit the 1KB request limit of JobControllerTest. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class JobControllerDocumentLimitTest {

    @Autowired
    private RestTestClient client;
    @MockitoBean
    private JobDispatcher dispatcher;

    @Test
    void post_1001Documents_returns400() {
        List<DocumentInput> docs = IntStream.range(0, 1001).mapToObj(i -> new DocumentInput("d" + i, "x")).toList();

        client.post().uri("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                .body(new CreateJobRequest(docs)).exchange().expectStatus().isBadRequest();
    }
}
