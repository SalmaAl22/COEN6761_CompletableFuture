
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsyncProcessor - Exception Handling Strategies")
public class AsyncProcessor_Test {

    private AsyncProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AsyncProcessor();
    }

    // ============================================================
    // Task A: Fail-Fast Tests
    // ============================================================

    @Test
    @DisplayName("Task A.1: FailFast - All services succeed, result aggregated")
    void testFailFastAllSuccess() throws ExecutionException, InterruptedException {
        List<Microservice> microservices = Arrays.asList(
                new Microservice("Service-A"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-A", "msg-B", "msg-C");

        CompletableFuture<String> future = processor.processAsyncFailFast(microservices, messages);
        String result = future.get();

        assertNotNull(result);
        assertTrue(result.contains("Service-A"));
        assertTrue(result.contains("Service-B"));
        assertTrue(result.contains("Service-C"));
        System.out.println("[TEST] FailFast result: " + result);
    }

    @Test
    @DisplayName("Task A.2: FailFast - First service fails, exception propagates")
    void testFailFastFirstServiceFails() {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");

        CompletableFuture<String> future = processor.processAsyncFailFast(services, messages);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Service-1 error"));
        System.out.println("[TEST] FailFast exception caught: " + exception.getCause().getMessage());
    }

    @Test
    @DisplayName("Task A.3: FailFast - Middle service fails, no partial results")
    void testFailFastMiddleServiceFails() {
        List<Microservice> services = List.of(
                new Microservice("Service-A"),
                failingService("Service-B", "Service-2 error"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");

        CompletableFuture<String> future = processor.processAsyncFailFast(services, messages);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    @DisplayName("Task A.4: FailFast - Size mismatch throws IllegalArgumentException")
    void testFailFastSizeMismatch() {
        List<Microservice> services = List.of(
                new Microservice("Service-A"),
                new Microservice("Service-B"));
        List<String> messages = List.of("msg-a"); // mismatch

        CompletableFuture<String> future = processor.processAsyncFailFast(services, messages);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    // ============================================================
    // Task B: Fail-Partial Tests
    // ============================================================

    @Test
    @DisplayName("Task B.1: FailPartial - All services succeed, all results returned")
    void testFailPartialAllSuccess() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                new Microservice("Service-A"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);
        List<String> results = future.get();

        assertEquals(3, results.size());
        assertTrue(results.contains("Service-A:MSG-A"));
        assertTrue(results.contains("Service-B:MSG-B"));
        assertTrue(results.contains("Service-C:MSG-C"));
        System.out.println("[TEST] FailPartial all success: " + results);
    }

    @Test
    @DisplayName("Task B.2: FailPartial - First service fails, partial results returned")
    void testFailPartialFirstServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);
        List<String> results = future.get();

        // Should return only successful results, no exception
        assertEquals(2, results.size());
        assertTrue(results.contains("Service-B:MSG-B"));
        assertTrue(results.contains("Service-C:MSG-C"));
        assertFalse(results.contains(null));
        System.out.println("[TEST] FailPartial partial results: " + results);
    }

    @Test
    @DisplayName("Task B.3: FailPartial - All services fail, empty list returned")
    void testFailPartialAllServicesFail() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                failingService("Service-B", "Service-2 error"),
                failingService("Service-C", "Service-3 error"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);
        List<String> results = future.get();

        // Should return empty list, no exception
        assertEquals(0, results.size());
        assertTrue(results.isEmpty());
        System.out.println("[TEST] FailPartial all failed, empty result: " + results);
    }

    @Test
    @DisplayName("Task B.4: FailPartial - No exception escapes to caller")
    void testFailPartialNoExceptionEscape() {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                new Microservice("Service-B"));
        List<String> messages = List.of("msg-a", "msg-b");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);

        // Should NOT throw; future should complete normally with partial results
        assertDoesNotThrow(() -> {
            List<String> results = future.get();
            assertEquals(1, results.size());
            assertEquals("Service-B:MSG-B", results.get(0));
        });
    }

    // ============================================================
    // Task C: Fail-Soft Tests
    // ============================================================

    @Test
    @DisplayName("Task C.1: FailSoft - All services succeed, result aggregated")
    void testFailSoftAllSuccess() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                new Microservice("Service-A"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");
        String fallback = "FALLBACK";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        assertNotEquals(fallback, result);
        assertTrue(result.contains("Service-A"));
        assertTrue(result.contains("Service-B"));
        assertTrue(result.contains("Service-C"));
        System.out.println("[TEST] FailSoft success result: " + result);
    }

    @Test
    @DisplayName("Task C.2: FailSoft - First service fails, fallback returned")
    void testFailSoftFirstServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                new Microservice("Service-B"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");
        String fallback = "DEFAULT_FALLBACK";
        String fallbackResults = "DEFAULT_FALLBACK Service-B:MSG-B Service-C:MSG-C";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        // Should return fallback, NOT the successful results
        assertEquals(fallbackResults, result);
        System.out.println("[TEST] FailSoft returned fallback: " + result);
    }

    @Test
    @DisplayName("Task C.3: FailSoft - Middle service fails, fallback returned")
    void testFailSoftMiddleServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                new Microservice("Service-A"),
                failingService("Service-B", "Service-2 error"),
                new Microservice("Service-C"));
        List<String> messages = List.of("msg-a", "msg-b", "msg-c");
        String fallback = "SERVICE_UNAVAILABLE";

        String fallbackResults = "Service-A:MSG-A SERVICE_UNAVAILABLE Service-C:MSG-C";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        assertEquals(fallbackResults, result);
    }

    @Test
    @DisplayName("Task C.4: FailSoft - No exception escapes, caller always gets result")
    void testFailSoftNoExceptionEscape() {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"),
                failingService("Service-B", "Service-2 error"));
        List<String> messages = List.of("msg-a", "msg-b");
        String fallback = "FAILED";
        String fallbackResults = "FAILED FAILED";
        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);

        // Should NOT throw; caller always gets a result
        assertDoesNotThrow(() -> {
            String result = future.get();
            assertEquals(fallbackResults, result);
        });
    }

    @Test
    @DisplayName("Task C.5: FailSoft - Custom fallback values")
    void testFailSoftCustomFallback() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-A", "Service-1 error"));
        List<String> messages = List.of("msg-a");

        String customFallback = "CUSTOM_ERROR_HANDLING";
        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, customFallback);
        String result = future.get();

        assertEquals(customFallback, result);
    }

    // ============================================================
    // Helper methods to create mock failed services
    // ============================================================

    private Microservice failingService(String serviceId, String errorMessage) {
        return new Microservice(serviceId) {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                CompletableFuture<String> cf = new CompletableFuture<>();
                cf.completeExceptionally(new RuntimeException(errorMessage));
                return cf;
            }
        };
    }

}