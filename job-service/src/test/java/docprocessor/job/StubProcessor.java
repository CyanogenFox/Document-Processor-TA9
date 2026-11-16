package docprocessor.job;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A scriptable fake Processor Service on a random port; records requests and the peak number in flight. */
public class StubProcessor implements AutoCloseable {

    /** One scripted answer; the last reply of a script repeats for every later call. */
    public record Reply(int status, String body, long delayMs) {
        public static Reply of(int status, String body) {
            return new Reply(status, body, 0);
        }
    }

    /** A request the stub received. */
    public record Received(String method, String path, String body) {
    }

    private static final Reply DEFAULT_POST = Reply.of(202, "{\"status\":\"QUEUED\"}");
    private static final Reply DEFAULT_OTHER = Reply.of(404, "{}");

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, List<Reply>> scripts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    public StubProcessor() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Scripts the answers for a method and exact path; calls beyond the script repeat its last reply. */
    public void script(String method, String path, Reply... replies) {
        String key = method + " " + path;
        calls.put(key, new AtomicInteger());
        scripts.put(key, List.of(replies));
    }

    /** Forgets all scripts and records. */
    public void reset() {
        scripts.clear();
        calls.clear();
        requests.clear();
        inFlight.set(0);
        maxInFlight.set(0);
    }

    public List<Received> requests() {
        return new ArrayList<>(requests);
    }

    public List<Received> requests(String method, String path) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().equals(path)).toList();
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Received(method, path, body));
            Reply reply = nextReply(method, path);
            if (reply.delayMs() > 0) {
                Thread.sleep(reply.delayMs());
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            boolean noBody = reply.status() == 204 || bytes.length == 0;
            exchange.sendResponseHeaders(reply.status(), noBody ? -1 : bytes.length);
            if (!noBody) {
                exchange.getResponseBody().write(bytes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
            inFlight.decrementAndGet();
        }
    }

    private Reply nextReply(String method, String path) {
        String key = method + " " + path;
        List<Reply> script = scripts.get(key);
        if (script == null) {
            return "POST".equals(method) ? DEFAULT_POST : DEFAULT_OTHER;
        }
        int index = calls.computeIfAbsent(key, k -> new AtomicInteger()).getAndIncrement();
        return script.get(Math.min(index, script.size() - 1));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
