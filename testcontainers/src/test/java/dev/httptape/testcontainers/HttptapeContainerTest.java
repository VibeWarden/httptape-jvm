package dev.httptape.testcontainers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttptapeContainer}.
 *
 * <p>These tests verify configuration logic, command assembly, and
 * classpath fixture discovery without requiring Docker. Integration
 * tests requiring a running container are in a separate class.
 */
class HttptapeContainerTest {

    @Nested
    class SseTimingModeTests {

        @Test
        void realtimeCliFlag() {
            assertEquals("realtime", SseTimingMode.REALTIME.toCliFlag());
        }

        @Test
        void instantCliFlag() {
            assertEquals("instant", SseTimingMode.INSTANT.toCliFlag());
        }

        @Test
        void acceleratedCliFlag() {
            assertEquals("accelerated=2.5", SseTimingMode.accelerated(2.5).toCliFlag());
        }

        @Test
        void acceleratedCliFlag_wholeNumber() {
            assertEquals("accelerated=3.0", SseTimingMode.accelerated(3.0).toCliFlag());
        }

        @Test
        void accelerated_zeroFactor_throws() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> SseTimingMode.accelerated(0));
            assertTrue(ex.getMessage().contains("positive"));
        }

        @Test
        void accelerated_negativeFactor_throws() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> SseTimingMode.accelerated(-1.5));
            assertTrue(ex.getMessage().contains("positive"));
        }

        @Test
        void sseTimingMode_sealedInterface_permits() {
            // Verify all three implementations are valid
            assertInstanceOf(SseTimingMode.class, SseTimingMode.REALTIME);
            assertInstanceOf(SseTimingMode.class, SseTimingMode.INSTANT);
            assertInstanceOf(SseTimingMode.class, SseTimingMode.accelerated(1.0));
        }
    }

    @Nested
    class CommandAssemblyTests {

        @Test
        void defaultCommand_hasServeAndFixtures() {
            var container = new HttptapeContainer();
            List<String> cmd = container.buildCommand();

            assertEquals("serve", cmd.get(0));
            assertEquals("--fixtures", cmd.get(1));
            assertEquals("/fixtures", cmd.get(2));
            assertEquals(3, cmd.size());
        }

        @Test
        void withSseTiming_addsFlag() {
            var container = new HttptapeContainer()
                    .withSseTiming(SseTimingMode.REALTIME);
            List<String> cmd = container.buildCommand();

            assertTrue(cmd.contains("--sse-timing=realtime"));
        }

        @Test
        void withSseTiming_accelerated() {
            var container = new HttptapeContainer()
                    .withSseTiming(SseTimingMode.accelerated(2.0));
            List<String> cmd = container.buildCommand();

            assertTrue(cmd.contains("--sse-timing=accelerated=2.0"));
        }

        @Test
        void withCors_addsFlag() {
            var container = new HttptapeContainer()
                    .withCors(true);
            List<String> cmd = container.buildCommand();

            assertTrue(cmd.contains("--cors"));
        }

        @Test
        void withCors_false_omitsFlag() {
            var container = new HttptapeContainer()
                    .withCors(false);
            List<String> cmd = container.buildCommand();

            assertFalse(cmd.contains("--cors"));
        }

        @Test
        void withDelay_addsDuration() {
            var container = new HttptapeContainer()
                    .withDelay(Duration.ofMillis(500));
            List<String> cmd = container.buildCommand();

            int idx = cmd.indexOf("--delay");
            assertTrue(idx >= 0, "Should contain --delay");
            assertEquals("500ms", cmd.get(idx + 1));
        }

        @Test
        void withDelay_zero_omitsFlag() {
            var container = new HttptapeContainer()
                    .withDelay(Duration.ZERO);
            List<String> cmd = container.buildCommand();

            assertFalse(cmd.contains("--delay"));
        }

        @Test
        void withErrorRate_addsFlag() {
            var container = new HttptapeContainer()
                    .withErrorRate(0.25);
            List<String> cmd = container.buildCommand();

            int idx = cmd.indexOf("--error-rate");
            assertTrue(idx >= 0, "Should contain --error-rate");
            assertEquals("0.25", cmd.get(idx + 1));
        }

        @Test
        void withFallbackStatus_addsFlag() {
            var container = new HttptapeContainer()
                    .withFallbackStatus(503);
            List<String> cmd = container.buildCommand();

            int idx = cmd.indexOf("--fallback-status");
            assertTrue(idx >= 0, "Should contain --fallback-status");
            assertEquals("503", cmd.get(idx + 1));
        }

        @Test
        void withReplayHeader_addsFlag() {
            var container = new HttptapeContainer()
                    .withReplayHeader("X-Mock", "true");
            List<String> cmd = container.buildCommand();

            int idx = cmd.indexOf("--replay-header");
            assertTrue(idx >= 0, "Should contain --replay-header");
            assertEquals("X-Mock=true", cmd.get(idx + 1));
        }

        @Test
        void withMultipleReplayHeaders_addsAllFlags() {
            var container = new HttptapeContainer()
                    .withReplayHeader("X-Mock", "true")
                    .withReplayHeader("X-Env", "test");
            List<String> cmd = container.buildCommand();

            long count = cmd.stream().filter("--replay-header"::equals).count();
            assertEquals(2, count, "Should have two --replay-header flags");
        }

        @Test
        void fullConfiguration_assemblesCorrectly() {
            var container = new HttptapeContainer()
                    .withSseTiming(SseTimingMode.INSTANT)
                    .withCors(true)
                    .withDelay(Duration.ofSeconds(1))
                    .withErrorRate(0.1)
                    .withFallbackStatus(503)
                    .withReplayHeader("X-Test", "yes");
            List<String> cmd = container.buildCommand();

            assertEquals("serve", cmd.get(0));
            assertTrue(cmd.contains("--sse-timing=instant"));
            assertTrue(cmd.contains("--cors"));
            assertTrue(cmd.contains("--delay"));
            assertTrue(cmd.contains("--error-rate"));
            assertTrue(cmd.contains("--fallback-status"));
            assertTrue(cmd.contains("--replay-header"));
        }

        @Test
        void withMatcherConfigFromClasspath_addsConfigFlag() {
            // Create a test resource for matcher config
            var container = new HttptapeContainer()
                    .withMatcherConfigFromClasspath("testfixtures/hello.json");
            List<String> cmd = container.buildCommand();

            assertTrue(cmd.contains("--config"));
            assertTrue(cmd.contains("/config/httptape.config.json"));
        }
    }

    @Nested
    class ErrorRateValidationTests {

        @Test
        void negativeRate_throws() {
            var container = new HttptapeContainer();
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> container.withErrorRate(-0.1));
            assertTrue(ex.getMessage().contains("0.0"));
        }

        @Test
        void rateAboveOne_throws() {
            var container = new HttptapeContainer();
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> container.withErrorRate(1.1));
            assertTrue(ex.getMessage().contains("1.0"));
        }

        @Test
        void zeroRate_allowed() {
            var container = new HttptapeContainer();
            assertDoesNotThrow(() -> container.withErrorRate(0.0));
        }

        @Test
        void oneRate_allowed() {
            var container = new HttptapeContainer();
            assertDoesNotThrow(() -> container.withErrorRate(1.0));
        }
    }

    @Nested
    class ClasspathFixtureDiscoveryTests {

        @Test
        void discoversJsonFiles_fromFilesystem() {
            Map<String, Path> fixtures =
                    HttptapeContainer.discoverClasspathFixtures("testfixtures/");

            assertTrue(fixtures.containsKey("hello.json"),
                    "Should find hello.json");
            assertTrue(fixtures.containsKey("world.json"),
                    "Should find world.json in subdir");
            assertEquals(2, fixtures.size());
        }

        @Test
        void detectsCollisions() {
            var ex = assertThrows(IllegalStateException.class,
                    () -> HttptapeContainer.discoverClasspathFixtures("collisionfixtures/"));
            assertTrue(ex.getMessage().contains("dup.json"),
                    "Error should mention the colliding filename");
            assertTrue(ex.getMessage().contains("collision"),
                    "Error should mention collision");
        }

        @Test
        void nonExistentRoot_throws() {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> HttptapeContainer.discoverClasspathFixtures("nonexistent/"));
            assertTrue(ex.getMessage().contains("not found"),
                    "Error should mention not found");
        }
    }

    @Nested
    class DefaultImageTests {

        @Test
        void defaultImage_isPinned() {
            assertEquals("ghcr.io/vibewarden/httptape:0.13.1",
                    HttptapeContainer.DEFAULT_IMAGE);
        }

        @Test
        void defaultConstructor_usesDefaultImage() {
            var container = new HttptapeContainer();
            assertEquals("ghcr.io/vibewarden/httptape:0.13.1",
                    container.getDockerImageName());
        }

        @Test
        void customImage_acceptsString() {
            // Verify custom image constructor compiles and the container can
            // be configured. We use the existing default image to avoid a
            // Docker pull failure for a non-existent tag.
            var container = new HttptapeContainer(HttptapeContainer.DEFAULT_IMAGE);
            assertEquals(HttptapeContainer.DEFAULT_IMAGE,
                    container.getDockerImageName());
        }
    }

    @Nested
    class FluentApiTests {

        @Test
        void fluentMethods_returnSameInstance() {
            var container = new HttptapeContainer();

            assertSame(container, container.withSseTiming(SseTimingMode.REALTIME));
            assertSame(container, container.withCors(true));
            assertSame(container, container.withReplayHeader("X-Test", "val"));
            assertSame(container, container.withDelay(Duration.ofMillis(100)));
            assertSame(container, container.withErrorRate(0.5));
            assertSame(container, container.withFallbackStatus(503));
        }
    }
}
