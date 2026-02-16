import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

//AsyncProcessor.java: coordinates many microservice futures.

public class AsyncProcessor {

        private static final long PER_SERVICE_TIMEOUT_MS = 500;

        private CompletableFuture<String> timedRetrieve(Microservice client, String message) {
                // Enforce liveness: a hanging service cannot block aggregation forever.
                return client.retrieveAsync(message)
                                .orTimeout(PER_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        // // processAsync: output order follows input list order (because you stream
        // // futures in list order after all complete).
        public CompletableFuture<String> processAsyncFailFast(List<Microservice> microservices, List<String> messages) {

                if (microservices.size() != messages.size()) {
                        return CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Services and messages size mismatch"));
                }

                List<CompletableFuture<String>> futures = IntStream.range(0, microservices.size())
                                .mapToObj(i -> timedRetrieve(microservices.get(i), messages.get(i)))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining(" ")));
        }

        public CompletableFuture<List<String>> processAsyncFailPartial(List<Microservice> microservices,
                        List<String> messages) {

                if (microservices.size() != messages.size()) {
                        return CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Services and messages size mismatch"));
                }

                List<CompletableFuture<String>> futures = IntStream.range(0, microservices.size())
                                .mapToObj(i -> timedRetrieve(microservices.get(i), messages.get(i))
                                                .exceptionally((ex) -> {
                                                        System.err.println("[FailPartial] Service failed: "
                                                                        + ex.getMessage());
                                                        return null;
                                                }))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .filter(result -> result != null)
                                                .collect(Collectors.toList()));

        }

        public CompletableFuture<String> processAsyncFailSoft(List<Microservice> microservices, List<String> messages,
                        String fallbackValue) {

                if (microservices.size() != messages.size()) {
                        return CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Services and messages size mismatch"));
                }

                List<CompletableFuture<String>> futures = IntStream.range(0, microservices.size())
                                .mapToObj(i -> timedRetrieve(microservices.get(i), messages.get(i))
                                                .exceptionally((ex) -> {
                                                        System.err.println("[FailSoft] Service failed: "
                                                                        + ex.getMessage());
                                                        return fallbackValue;
                                                }))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining(" ")));

        }

        // In processAsyncCompletionOrder, each future adds to a synchronized list when
        // it completes
        // so list order is fastest-first (non-deterministic).
        public CompletableFuture<List<String>> processAsyncCompletionOrder(
                        List<Microservice> microservices, String message) {

                List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

                List<CompletableFuture<Void>> futures = microservices.stream()
                                .map(ms -> timedRetrieve(ms, message)
                                                .thenAccept(completionOrder::add))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> completionOrder);

        }

}
