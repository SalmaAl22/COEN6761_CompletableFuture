import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

//AsyncProcessor.java: coordinates many microservice futures.

public class AsyncProcessor {

        // // processAsync: output order follows input list order (because you stream
        // // futures in list order after all complete).
        public CompletableFuture<String> processAsyncFailFast(List<Microservice> microservices, List<String> messages) {

                if (microservices.size() != messages.size()) {
                        return CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Services and messages size mismatch"));
                }

                List<CompletableFuture<String>> futures = microservices.stream()
                                .map(client -> client.retrieveAsync(messages.get(microservices.indexOf(client))))
                                .collect(Collectors.toList()); // collect(toList()) just gathers those

                // wait for them to complete.

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])) // allOf(...) = “wait until
                                                                                          // all futures are done.”
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

                List<CompletableFuture<String>> futures = microservices.stream()
                                .map(client -> client.retrieveAsync(messages.get(microservices.indexOf(client)))
                                                .exceptionally((ex) -> {
                                                        System.err.println("[FailPartial] Service failed: "
                                                                        + ex.getMessage());
                                                        return null;
                                                }))

                                .collect(Collectors.toList()); // collect(toList()) just gathers those

                // wait for them to complete.

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])) // allOf(...) = “wait until
                                                                                          // all futures are done.”
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .filter(result -> result != null) // skip failed services
                                                .collect(Collectors.toList()));

        }

        public CompletableFuture<String> processAsyncFailSoft(List<Microservice> microservices, List<String> messages,
                        String fallbackValue) {

                if (microservices.size() != messages.size()) {
                        return CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Services and messages size mismatch"));
                }

                List<CompletableFuture<String>> futures = microservices.stream()
                                .map(client -> client.retrieveAsync(messages.get(microservices.indexOf(client)))
                                                .exceptionally((ex) -> {
                                                        System.err.println("[FailSoft] Service failed: "
                                                                        + ex.getMessage());
                                                        return fallbackValue;
                                                }))
                                .collect(Collectors.toList()); // collect(toList()) just gathers those

                // wait for them to complete.

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])) // allOf(...) = “wait until
                                                                                          // all futures are done.”
                                .thenApply(v -> futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining(" ")));

        }

        // In processAsyncCompletionOrder, each future adds to a synchronized list when
        // it completes
        // so list order is fastest-first (non-deterministic).
        // processAsyncCompletionOrder: order changes run-to-run because of random
        // delays.
        public CompletableFuture<List<String>> processAsyncCompletionOrder(
                        List<Microservice> microservices, String message) {

                List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

                List<CompletableFuture<Void>> futures = microservices.stream()
                                .map(ms -> ms.retrieveAsync(message)
                                                .thenAccept(completionOrder::add))
                                .collect(Collectors.toList());

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> completionOrder);

        }

}
