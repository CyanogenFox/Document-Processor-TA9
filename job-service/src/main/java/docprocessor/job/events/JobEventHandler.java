package docprocessor.job.events;

import docprocessor.contracts.JobEvent;
import docprocessor.job.service.JobLifecycle;
import docprocessor.job.service.JobSync;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

/** Receives Processor events; a bad frame is logged and dropped so the connection stays open. */
@Slf4j
@Component
public class JobEventHandler extends TextWebSocketHandler {

    private final JobLifecycle lifecycle;
    private final JobSync jobSync;
    private final JsonMapper jsonMapper;
    private final TaskExecutor executor;

    public JobEventHandler(JobLifecycle lifecycle, JobSync jobSync, JsonMapper jsonMapper,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.lifecycle = lifecycle;
        this.jobSync = jobSync;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Processor connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Processor disconnected: {} ({})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JobEvent event = jsonMapper.readValue(message.getPayload(), JobEvent.class);
            if (event == null || event.jobId() == null || event.type() == null) {
                log.warn("Ignored event without jobId or type");
                return;
            }
            handle(event);
        } catch (Exception e) {
            log.warn("Ignored unreadable event: {}", e.toString());
        }
    }

    /** STARTED is applied inline; COMPLETED and FAILED fetch the result in the background. */
    private void handle(JobEvent event) {
        UUID jobId = event.jobId();
        MDC.put("jobId", jobId.toString());
        try {
            log.info("Event {} for job {}{}", event.type(), jobId, event.reason() == null ? "" : " (" + event.reason() + ")");
            switch (event.type()) {
                case STARTED -> lifecycle.started(jobId);
                case COMPLETED, FAILED -> executor.execute(() -> syncInBackground(jobId));
            }
        } finally {
            MDC.remove("jobId");
        }
    }

    private void syncInBackground(UUID jobId) {
        MDC.put("jobId", jobId.toString());
        try {
            jobSync.sync(jobId);
        } catch (Exception e) {
            log.error("Sync of job {} failed unexpectedly", jobId, e);
        } finally {
            MDC.remove("jobId");
        }
    }
}
