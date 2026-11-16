package docprocessor.processor.events;

import docprocessor.contracts.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

/** Sends job events to the Job Service as JSON text; drops them when not connected. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketJobEventPublisher implements JobEventPublisher {

    private final JobServiceConnection connection;
    private final JsonMapper jsonMapper;

    // Never throws: a failed report must not break the worker.
    @Override
    public void publish(JobEvent event) {
        try {
            WebSocketSession session = connection.session().orElse(null);
            if (session == null) {
                log.warn("Job {} event {} dropped, not connected", event.jobId(), event.type());
                return;
            }
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(event)));
            log.info("Job {} event {} sent", event.jobId(), event.type());
        } catch (Exception e) {
            log.warn("Job {} event {} could not be sent: {}", event.jobId(), event.type(), e.toString());
        }
    }
}
