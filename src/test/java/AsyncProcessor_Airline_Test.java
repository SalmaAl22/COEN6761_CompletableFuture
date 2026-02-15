import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsyncProcessor - Airline Quote Policies (no Mockito)")
public class AsyncProcessor_Airline_Test {

    private AsyncProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AsyncProcessor();
    }

    @Test
    @DisplayName("FailFast: all airline quote services succeed")
    void failFast_allSuccess(TestReporter reporter) {
        List<Microservice> services = List.of(
                new Microservice("AirAlpha"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        String actual = processor.processAsyncFailFast(services, messages).join();
        String expected = "AirAlpha:NYC-LAX JetBravo:NYC-SFO SkyCharlie:NYC-SEA";

        logExpectedActual(reporter, "failFast_allSuccess", expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("FailFast: one airline API fails, whole request fails")
    void failFast_oneFails(TestReporter reporter) {
        List<Microservice> services = List.of(
                failingService("AirAlpha", "AirAlpha API down"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> processor.processAsyncFailFast(services, messages).join());

        Throwable root = rootCause(ex);
        logExpectedActual(reporter, "failFast_oneFails", "RuntimeException containing 'AirAlpha API down'",
                root.getClass().getSimpleName() + ": " + root.getMessage());
        assertTrue(root instanceof RuntimeException);
        assertTrue(root.getMessage().contains("AirAlpha API down"));
    }

    @Test
    @DisplayName("FailFast: size mismatch throws IllegalArgumentException")
    void failFast_sizeMismatch(TestReporter reporter) {
        List<Microservice> services = List.of(new Microservice("AirAlpha"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo");

        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> processor.processAsyncFailFast(services, messages).join());

        Throwable root = rootCause(ex);
        logExpectedActual(reporter, "failFast_sizeMismatch", "IllegalArgumentException",
                root.getClass().getSimpleName() + ": " + root.getMessage());
        assertTrue(root instanceof IllegalArgumentException);
    }

    @Test
    @DisplayName("FailFast Liveness: hanging service times out and does not block forever")
    void failFast_liveness_timeout(TestReporter reporter) {
        List<Microservice> services = List.of(
                hangingService("StuckAir"),
                new Microservice("JetBravo"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            CompletionException ex = assertThrows(
                    CompletionException.class,
                    () -> processor.processAsyncFailFast(services, messages).join());
            Throwable root = rootCause(ex);
            logExpectedActual(reporter, "failFast_liveness_timeout", "TimeoutException",
                    root.getClass().getSimpleName());
            assertTrue(root instanceof TimeoutException);
        });
    }

    @Test
    @DisplayName("FailPartial: all airline quotes succeed")
    void failPartial_allSuccess(TestReporter reporter) {
        List<Microservice> services = List.of(
                new Microservice("AirAlpha"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        List<String> actual = processor.processAsyncFailPartial(services, messages).join();
        List<String> expected = List.of(
                "AirAlpha:NYC-LAX",
                "JetBravo:NYC-SFO",
                "SkyCharlie:NYC-SEA");

        logExpectedActual(reporter, "failPartial_allSuccess", expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("FailPartial: failed airline quote is excluded")
    void failPartial_oneFails(TestReporter reporter) {
        List<Microservice> services = List.of(
                failingService("AirAlpha", "AirAlpha timeout"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        List<String> actual = processor.processAsyncFailPartial(services, messages).join();
        List<String> expected = List.of("JetBravo:NYC-SFO", "SkyCharlie:NYC-SEA");

        logExpectedActual(reporter, "failPartial_oneFails", expected, actual);
        assertEquals(2, actual.size());
        assertTrue(actual.contains("JetBravo:NYC-SFO"));
        assertTrue(actual.contains("SkyCharlie:NYC-SEA"));
        assertFalse(actual.contains(null));
    }

    @Test
    @DisplayName("FailPartial: all airline APIs fail -> empty result list")
    void failPartial_allFail(TestReporter reporter) {
        List<Microservice> services = List.of(
                failingService("AirAlpha", "AirAlpha down"),
                failingService("JetBravo", "JetBravo down"),
                failingService("SkyCharlie", "SkyCharlie down"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        List<String> actual = processor.processAsyncFailPartial(services, messages).join();
        List<String> expected = List.of();

        logExpectedActual(reporter, "failPartial_allFail", expected, actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    @DisplayName("FailPartial Liveness: hanging service gets dropped after timeout")
    void failPartial_liveness_timeout(TestReporter reporter) {
        List<Microservice> services = List.of(
                hangingService("StuckAir"),
                new Microservice("JetBravo"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            List<String> actual = processor.processAsyncFailPartial(services, messages).join();
            List<String> expected = List.of("JetBravo:NYC-SFO");
            logExpectedActual(reporter, "failPartial_liveness_timeout", expected, actual);
            assertEquals(1, actual.size());
            assertEquals("JetBravo:NYC-SFO", actual.get(0));
        });
    }

    @Test
    @DisplayName("FailSoft: all airline quote services succeed")
    void failSoft_allSuccess(TestReporter reporter) {
        List<Microservice> services = List.of(
                new Microservice("AirAlpha"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        String actual = processor
                .processAsyncFailSoft(services, messages, "QUOTE_UNAVAILABLE")
                .join();
        String expected = "AirAlpha:NYC-LAX JetBravo:NYC-SFO SkyCharlie:NYC-SEA";

        logExpectedActual(reporter, "failSoft_allSuccess", expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("FailSoft: failed airline quote replaced with fallback")
    void failSoft_oneFails(TestReporter reporter) {
        List<Microservice> services = List.of(
                new Microservice("AirAlpha"),
                failingService("JetBravo", "JetBravo timeout"),
                new Microservice("SkyCharlie"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo", "nyc-sea");

        String actual = processor
                .processAsyncFailSoft(services, messages, "QUOTE_UNAVAILABLE")
                .join();
        String expected = "AirAlpha:NYC-LAX QUOTE_UNAVAILABLE SkyCharlie:NYC-SEA";

        logExpectedActual(reporter, "failSoft_oneFails", expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("FailSoft: all airline APIs fail but caller still gets result")
    void failSoft_allFail(TestReporter reporter) {
        List<Microservice> services = List.of(
                failingService("AirAlpha", "AirAlpha down"),
                failingService("JetBravo", "JetBravo down"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo");

        String actual = processor
                .processAsyncFailSoft(services, messages, "QUOTE_UNAVAILABLE")
                .join();
        String expected = "QUOTE_UNAVAILABLE QUOTE_UNAVAILABLE";

        logExpectedActual(reporter, "failSoft_allFail", expected, actual);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("FailSoft Liveness: hanging service gets fallback after timeout")
    void failSoft_liveness_timeout(TestReporter reporter) {
        List<Microservice> services = List.of(
                hangingService("StuckAir"),
                new Microservice("JetBravo"));
        List<String> messages = List.of("nyc-lax", "nyc-sfo");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String actual = processor
                    .processAsyncFailSoft(services, messages, "QUOTE_UNAVAILABLE")
                    .join();
            String expected = "QUOTE_UNAVAILABLE JetBravo:NYC-SFO";
            logExpectedActual(reporter, "failSoft_liveness_timeout", expected, actual);
            assertEquals(expected, actual);
        });
    }

    @Test
    @DisplayName("CompletionOrder: all airline quotes returned, order may vary")
    void completionOrder_observed_notFixed(TestReporter reporter) {
        List<Microservice> services = List.of(
                new Microservice("AirAlpha"),
                new Microservice("JetBravo"),
                new Microservice("SkyCharlie"));

        List<String> expectedMembers = List.of("AirAlpha:NYC-LAX", "JetBravo:NYC-LAX", "SkyCharlie:NYC-LAX");

        for (int i = 0; i < 8; i++) {
            List<String> actual = processor.processAsyncCompletionOrder(services, "nyc-lax").join();
            logExpectedActual(reporter, "completionOrder_run_" + (i + 1), expectedMembers, actual);
            assertEquals(3, actual.size());
            assertTrue(actual.contains("AirAlpha:NYC-LAX"));
            assertTrue(actual.contains("JetBravo:NYC-LAX"));
            assertTrue(actual.contains("SkyCharlie:NYC-LAX"));
        }
    }

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

    private Microservice hangingService(String serviceId) {
        return new Microservice(serviceId) {
            @Override
            public CompletableFuture<String> retrieveAsync(String input) {
                return new CompletableFuture<>(); // never completes
            }
        };
    }

    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    private void logExpectedActual(TestReporter reporter, String testName, Object expected, Object actual) {
        reporter.publishEntry("test", testName);
        reporter.publishEntry("expected", String.valueOf(expected));
        reporter.publishEntry("actual", String.valueOf(actual));
        System.out.println("[TEST OUTPUT] " + testName + " expected=[" + expected + "] actual=[" + actual + "]");
    }
}
