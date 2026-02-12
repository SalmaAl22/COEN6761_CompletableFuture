
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
    // private Microservice new Microservice(String result) {
    // return new Microservice("mock") {
    // @Override
    // public CompletableFuture<String> retrieveAsync(String input) {
    // return CompletableFuture.completedFuture(result);
    // }
    // };
    // }
    private Microservice microservice;

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
                new Microservice("svc-a"),
                new Microservice("svc-b"),
                new Microservice("svc-c"));
        List<String> messages = List.of("msg-A", "msg-B", "msg-C");

        CompletableFuture<String> future = processor.processAsyncFailFast(microservices, messages);
        String result = future.get();

        assertNotNull(result);
        assertTrue(result.contains("result-A"));
        assertTrue(result.contains("result-B"));
        assertTrue(result.contains("result-C"));
        System.out.println("[TEST] FailFast result: " + result);
    }

    @Test
    @DisplayName("Task A.2: FailFast - First service fails, exception propagates")
    void testFailFastFirstServiceFails() {
        List<Microservice> services = List.of(
                failingService("Service-1 error"),
                new Microservice("result-2"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");

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
                new Microservice("result-1"),
                failingService("Service-2 error"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");

        CompletableFuture<String> future = processor.processAsyncFailFast(services, messages);

        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    @DisplayName("Task A.4: FailFast - Size mismatch throws IllegalArgumentException")
    void testFailFastSizeMismatch() {
        List<Microservice> services = List.of(
                new Microservice("result-1"),
                new Microservice("result-2"));
        List<String> messages = List.of("msg-1"); // mismatch

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
                new Microservice("result-1"),
                new Microservice("result-2"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);
        List<String> results = future.get();

        assertEquals(3, results.size());
        assertTrue(results.contains("result-1"));
        assertTrue(results.contains("result-2"));
        assertTrue(results.contains("result-3"));
        System.out.println("[TEST] FailPartial all success: " + results);
    }

    @Test
    @DisplayName("Task B.2: FailPartial - First service fails, partial results returned")
    void testFailPartialFirstServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-1 error"),
                new Microservice("result-2"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);
        List<String> results = future.get();

        // Should return only successful results, no exception
        assertEquals(2, results.size());
        assertTrue(results.contains("result-2"));
        assertTrue(results.contains("result-3"));
        assertFalse(results.contains(null));
        System.out.println("[TEST] FailPartial partial results: " + results);
    }

    @Test
    @DisplayName("Task B.3: FailPartial - All services fail, empty list returned")
    void testFailPartialAllServicesFail() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-1 error"),
                failingService("Service-2 error"),
                failingService("Service-3 error"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");

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
                failingService("Service-1 error"),
                new Microservice("result-2"));
        List<String> messages = List.of("msg-1", "msg-2");

        CompletableFuture<List<String>> future = processor.processAsyncFailPartial(services, messages);

        // Should NOT throw; future should complete normally with partial results
        assertDoesNotThrow(() -> {
            List<String> results = future.get();
            assertEquals(1, results.size());
            assertEquals("result-2", results.get(0));
        });
    }

    // ============================================================
    // Task C: Fail-Soft Tests
    // ============================================================

    @Test
    @DisplayName("Task C.1: FailSoft - All services succeed, result aggregated")
    void testFailSoftAllSuccess() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                new Microservice("result-1"),
                new Microservice("result-2"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");
        String fallback = "FALLBACK";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        assertNotEquals(fallback, result);
        assertTrue(result.contains("result-1"));
        assertTrue(result.contains("result-2"));
        assertTrue(result.contains("result-3"));
        System.out.println("[TEST] FailSoft success result: " + result);
    }

    @Test
    @DisplayName("Task C.2: FailSoft - First service fails, fallback returned")
    void testFailSoftFirstServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Service-1 error"),
                new Microservice("result-2"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");
        String fallback = "DEFAULT_FALLBACK";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        // Should return fallback, NOT the successful results
        assertEquals(fallback, result);
        System.out.println("[TEST] FailSoft returned fallback: " + result);
    }

    @Test
    @DisplayName("Task C.3: FailSoft - Middle service fails, fallback returned")
    void testFailSoftMiddleServiceFails() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                new Microservice("result-1"),
                failingService("Service-2 error"),
                new Microservice("result-3"));
        List<String> messages = List.of("msg-1", "msg-2", "msg-3");
        String fallback = "SERVICE_UNAVAILABLE";

        String fallbackResults = "result-1:MSG-1 SERVICE_UNAVAILABLE result-3:MSG-3";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);
        String result = future.get();

        assertEquals(fallbackResults, result);
    }

    @Test
    @DisplayName("Task C.4: FailSoft - No exception escapes, caller always gets result")
    void testFailSoftNoExceptionEscape() {
        List<Microservice> services = List.of(
                failingService("Service-1 error"),
                failingService("Service-2 error"));
        List<String> messages = List.of("msg-1", "msg-2");
        String fallback = "ALL_FAILED";

        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, fallback);

        // Should NOT throw; caller always gets a result
        assertDoesNotThrow(() -> {
            String result = future.get();
            assertEquals(fallback, result);
        });
    }

    @Test
    @DisplayName("Task C.5: FailSoft - Custom fallback values")
    void testFailSoftCustomFallback() throws ExecutionException, InterruptedException {
        List<Microservice> services = List.of(
                failingService("Error"));
        List<String> messages = List.of("msg-1");

        String customFallback = "CUSTOM_ERROR_HANDLING";
        CompletableFuture<String> future = processor.processAsyncFailSoft(services, messages, customFallback);
        String result = future.get();

        assertEquals(customFallback, result);
    }

    // ============================================================
    // Helper methods to create mock failed services
    // ============================================================

    private Microservice failingService(String id) {
        return new Microservice(id) {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                CompletableFuture<String> cf = new CompletableFuture<>();
                cf.completeExceptionally(new RuntimeException("simulated failure"));
                return cf;
            }
        };
    }

}