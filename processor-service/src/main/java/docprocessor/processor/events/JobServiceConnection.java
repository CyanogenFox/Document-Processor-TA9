package docprocessor.processor.events;

import docprocessor.processor.config.ProcessorProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Keeps one WebSocket connection to the Job Service open, reconnecting with backoff. */
@Slf4j
@Component
public class JobServiceConnection implements SmartLifecycle {

    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    // A slow Job Service may block a sender for at most 10 s; up to 512 KB may queue before the session is closed.
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final ProcessorProperties properties;
    private final StandardWebSocketClient client = new StandardWebSocketClient();

    private volatile WebSocketSession current;
    private volatile Thread thread;

    public JobServiceConnection(ProcessorProperties properties) {
        this.properties = properties;
    }

    /** The currently open session, or empty while disconnected. */
    public Optional<WebSocketSession> session() {
        return Optional.ofNullable(current).filter(WebSocketSession::isOpen);
    }

    @Override
    public void start() {
        thread = Thread.ofVirtual().name("job-service-connection").start(this::connectLoop);
    }

    @Override
    public void stop() {
        Thread running = thread;
        thread = null;
        if (running != null) {
            running.interrupt();
        }
        closeCurrent();
    }

    @Override
    public boolean isRunning() {
        return thread != null;
    }

    // Connect, wait until the connection closes, pause, repeat; the pause doubles while connecting keeps failing.
    private void connectLoop() {
        Duration delay = properties.reconnectDelay();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                boolean connected = false;
                try {
                    connected = connectAndWaitForClose();
                } catch (RuntimeException e) {
                    log.warn("Job Service connection loop failed: {}", e.toString());
                }
                if (connected) {
                    delay = properties.reconnectDelay();
                    log.info("Reconnecting to Job Service in {}", delay);
                } else {
                    log.warn("Job Service not reachable at {}, retrying in {}", properties.jobServiceWsUrl(), delay);
                }
                Thread.sleep(delay);
                if (!connected) {
                    delay = nextDelay(delay, properties.maxReconnectDelay());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Returns true if the connection was established, false if connecting failed.
    private boolean connectAndWaitForClose() throws InterruptedException {
        String url = properties.jobServiceWsUrl().toString();
        ConnectionHandler handler = new ConnectionHandler();
        WebSocketSession raw;
        try {
            raw = client.execute(handler, url).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
        try {
            current = new ConcurrentWebSocketSessionDecorator(raw, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
            log.info("Connected to Job Service at {}", url);
            handler.closed.get();
        } catch (ExecutionException e) {
            // never happens: the future is only ever completed normally
        } finally {
            current = null;
            closeQuietly(raw); // this thread owns the session, so shutdown never leaks it
            log.info("Disconnected from Job Service at {}", url);
        }
        return true;
    }

    // Doubles the delay, capped at max.
    static Duration nextDelay(Duration delay, Duration max) {
        Duration doubled = delay.multipliedBy(2);
        return doubled.compareTo(max) > 0 ? max : doubled;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (Exception e) {
            log.warn("Could not close Job Service session: {}", e.toString());
        }
    }

    private void closeCurrent() {
        WebSocketSession open = current;
        if (open != null) {
            closeQuietly(open);
        }
    }

    /** Completes {@code closed} when this one connection ends, for any reason. */
    private static class ConnectionHandler extends TextWebSocketHandler {

        final CompletableFuture<Void> closed = new CompletableFuture<>();

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(null);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            closed.complete(null);
        }
    }
}
