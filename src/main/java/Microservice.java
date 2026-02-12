
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

class Microservice {

    private final String serviceId;

    // Microservice.java: simulates one remote service call and returns a future
    // result.

    public Microservice(String serviceId) {
        this.serviceId = serviceId;
    }

    // public CompletableFuture<String> retrieveAsync(String input) {
    // // include input in the output so tests can verify the passed message
    // return CompletableFuture.supplyAsync(() -> serviceId + ":" +
    // input.toUpperCase());
    // }
    public CompletableFuture<String> retrieveAsync(String input) {
        return CompletableFuture.supplyAsync(() -> { // supplyAsync(...) = run work on another thread.
            // jitter: 0..30ms to perturb scheduling
            int delayMs = ThreadLocalRandom.current().nextInt(0, 31);
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return serviceId + ":" + input.toUpperCase();
            // return serviceId + ":" + input.toUpperCase() + "(" + delayMs + "ms)";
        });
    }

}