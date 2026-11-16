package docprocessor.processor.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import docprocessor.contracts.JobEvent;
import docprocessor.contracts.JobEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/** Runs the Processor with a fake Job Service WebSocket endpoint on its own port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class WebSocketJobEventPublisherTest {

    private static final int PORT = TestSocketUtils.findAvailableTcpPort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("processor.job-service-ws-url", () -> "ws://localhost:" + PORT + "/ws/events");
        registry.add("processor.reconnect-delay", () -> "100ms");
    }

    @Autowired
    private JobEventPublisher publisher;

    @Autowired
    private RecordingHandler handler;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void publish_connected_serverReceivesJson() throws Exception {
        await().until(() -> !handler.openSessions.isEmpty());
        JobEvent event = new JobEvent(UUID.randomUUID(), JobEventType.STARTED, Instant.now(), null);

        publisher.publish(event);

        await().until(() -> handler.received.stream().anyMatch(t -> t.contains(event.jobId().toString())));
        String text = handler.received.stream()
                .filter(t -> t.contains(event.jobId().toString())).findFirst().orElseThrow();
        assertThat(jsonMapper.readValue(text, JobEvent.class)).isEqualTo(event);
    }

    @Test
    void publish_afterServerClosedSession_reconnectsAndDelivers() throws Exception {
        await().until(() -> !handler.openSessions.isEmpty());
        WebSocketSession first = handler.openSessions.get(0);

        first.close();
        await().until(() -> handler.openSessions.stream().anyMatch(s -> s != first && s.isOpen()));
        JobEvent event = new JobEvent(UUID.randomUUID(), JobEventType.COMPLETED, Instant.now(), null);
        publisher.publish(event);

        await().until(() -> handler.received.stream().anyMatch(t -> t.contains(event.jobId().toString())));
    }

    /** Test Job Service endpoint: records received texts and the sessions that connected. */
    static class RecordingHandler extends TextWebSocketHandler {

        final List<String> received = new CopyOnWriteArrayList<>();
        final List<WebSocketSession> openSessions = new CopyOnWriteArrayList<>();

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            openSessions.add(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            received.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            openSessions.remove(session);
        }
    }

    @TestConfiguration
    @EnableWebSocket
    static class FakeJobServiceConfig implements WebSocketConfigurer {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            registry.addHandler(recordingHandler(), "/ws/events");
        }
    }
}
