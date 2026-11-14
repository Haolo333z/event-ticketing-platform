import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Transport only: long-lived HTTP/1.1 client, real platform threads, monotonic times. */
public class HttpBurst {
    static String quote(String s) {
        if (s == null) return "null";
        var out = new StringBuilder("\"");
        for (char c : s.toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        URI base = URI.create(args[0]); int threads = Integer.parseInt(args[1]);
        if (!"http".equals(base.getScheme()) || !"127.0.0.1".equals(base.getHost()) || base.getPort() < 1 || threads < 50)
            throw new IllegalArgumentException("Only an owned loopback HTTP target, >=50 workers");
        String token = System.getenv("LOAD_CUSTOMER_TOKEN");
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing test fixture auth");
        var sequence = new AtomicInteger();
        var executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> Thread.ofPlatform().name("load-worker-" + sequence.incrementAndGet()).unstarted(r));
        executor.prestartAllCoreThreads();
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3)).build();
             var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("READY"); System.out.flush();
            for (String line; (line = input.readLine()) != null;) {
                if (line.equals("QUIT")) break;
                Properties p = new Properties();
                try (var reader = Files.newBufferedReader(Path.of(line))) { p.load(reader); }
                int requests = Integer.parseInt(p.getProperty("requests"));
                if (requests < 1 || requests > threads) throw new IllegalArgumentException("Requests must fit simultaneous workers");
                String even = p.getProperty("bodyEven"), odd = p.getProperty("bodyOdd", even);
                var ready = new CountDownLatch(requests); var gate = new CountDownLatch(1);
                var epoch = new AtomicLong(); var active = new AtomicInteger(); var peak = new AtomicInteger();
                var lastEnd = new AtomicLong(); var futures = new ArrayList<Future<String>>();
                for (int i = 0; i < requests; i++) {
                    String body = i % 2 == 0 ? even : odd;
                    futures.add(executor.submit(() -> {
                        String id = UUID.randomUUID().toString();
                        var request = HttpRequest.newBuilder(base.resolve("/api/bookings"))
                            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token).header("Idempotency-Key", id)
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                        ready.countDown(); gate.await();
                        long start = System.nanoTime(); peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                        int status = 0; String response = null, error = null;
                        try {
                            var result = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                            status = result.statusCode(); response = result.body();
                        } catch (Exception ex) { error = ex.getClass().getSimpleName(); }
                        long end = System.nanoTime(); active.decrementAndGet(); lastEnd.accumulateAndGet(end, Math::max);
                        return "{\"requestId\":" + quote(id) + ",\"worker\":" + quote(Thread.currentThread().getName())
                            + ",\"startOffsetMs\":" + ((start - epoch.get()) / 1e6) + ",\"latencyMs\":" + ((end - start) / 1e6)
                            + ",\"status\":" + status + ",\"transportError\":" + quote(error)
                            + ",\"responseBody\":" + quote(response) + "}";
                    }));
                }
                if (!ready.await(15, TimeUnit.SECONDS)) { gate.countDown(); throw new IllegalStateException("Worker readiness timeout"); }
                epoch.set(System.nanoTime()); gate.countDown();
                var rows = new ArrayList<String>();
                for (var f : futures) rows.add(f.get(25, TimeUnit.SECONDS));
                String result = "{\"requests\":" + requests + ",\"workerThreads\":" + threads
                    + ",\"readyWorkers\":" + requests + ",\"peakInFlight\":" + peak.get()
                    + ",\"elapsedMs\":" + ((lastEnd.get() - epoch.get()) / 1e6)
                    + ",\"samples\":[" + String.join(",", rows) + "]}";
                Files.writeString(Path.of(p.getProperty("output")), result, StandardCharsets.UTF_8);
                System.out.println("RESULT"); System.out.flush();
            }
        } finally { executor.shutdownNow(); }
    }
}
