package dev.httptape.testcontainers;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Testcontainers wrapper for the httptape HTTP mock server.
 *
 * <p>Provides a fluent Java API to configure an httptape container
 * for recording/replay in integration tests. Replaces ~50 lines of
 * manual {@link GenericContainer} setup with ~5 lines.
 *
 * <p>Usage:
 * <pre>{@code
 * var container = new HttptapeContainer()
 *     .withFixturesFromClasspath("fixtures/")
 *     .withSseTiming(SseTimingMode.REALTIME);
 * container.start();
 * String baseUrl = container.getBaseUrl();
 * }</pre>
 */
public class HttptapeContainer extends GenericContainer<HttptapeContainer> {

    /** Default httptape Docker image, pinned to the SDK release's tested version. */
    public static final String DEFAULT_IMAGE = "ghcr.io/vibewarden/httptape:0.13.1";

    /** The port httptape listens on inside the container. */
    static final int HTTPTAPE_PORT = 8081;

    /** Container path where fixtures are mounted. */
    private static final String CONTAINER_FIXTURES_PATH = "/fixtures";

    /** Container path where the matcher config is mounted. */
    private static final String CONTAINER_CONFIG_PATH = "/config/httptape.config.json";

    private final Map<String, String> replayHeaders = new LinkedHashMap<>();
    private SseTimingMode sseTiming;
    private boolean corsEnabled;
    private Duration delay;
    private double errorRate;
    private int fallbackStatus = -1;
    private boolean hasMatcherConfig;

    /**
     * Creates an HttptapeContainer with the default image.
     */
    public HttptapeContainer() {
        this(DEFAULT_IMAGE);
    }

    /**
     * Creates an HttptapeContainer with a custom image.
     *
     * @param dockerImageName full Docker image reference (e.g., "ghcr.io/vibewarden/httptape:0.14.0")
     */
    public HttptapeContainer(String dockerImageName) {
        super(dockerImageName);
        withExposedPorts(HTTPTAPE_PORT);
        waitingFor(Wait.forHttp("/").forStatusCode(404));
    }

    /**
     * Mounts a host directory as the fixture source.
     *
     * @param hostPath path to the fixtures directory on the host
     * @return this container for chaining
     */
    public HttptapeContainer withFixturesPath(Path hostPath) {
        withFileSystemBind(hostPath.toAbsolutePath().toString(), CONTAINER_FIXTURES_PATH,
                BindMode.READ_ONLY);
        return self();
    }

    /**
     * Discovers and mounts fixture files from the classpath.
     *
     * <p>Scans the given classpath root for {@code *.json} files,
     * flattens subdirectories, and copies each file into the container's
     * {@code /fixtures} directory. Fails fast on filename collisions
     * across subdirectories.
     *
     * <p>This replaces the manual
     * {@code MountableFile.forClasspathResource(...)} + {@code withCopyFileToContainer(...)}
     * pattern and Spring's {@code PathMatchingResourcePatternResolver}.
     *
     * @param classpathRoot classpath prefix (e.g., "fixtures/")
     * @return this container for chaining
     * @throws IllegalArgumentException if the classpath root is not found
     * @throws IllegalStateException    if filename collisions are detected
     */
    public HttptapeContainer withFixturesFromClasspath(String classpathRoot) {
        Map<String, Path> fixtures = discoverClasspathFixtures(classpathRoot);
        fixtures.forEach((name, path) ->
                withCopyFileToContainer(
                        MountableFile.forHostPath(path),
                        CONTAINER_FIXTURES_PATH + "/" + name));
        return self();
    }

    /**
     * Mounts a matcher config file from a host path.
     *
     * @param configFile path to the httptape config JSON on the host
     * @return this container for chaining
     */
    public HttptapeContainer withMatcherConfig(Path configFile) {
        withCopyFileToContainer(
                MountableFile.forHostPath(configFile),
                CONTAINER_CONFIG_PATH);
        hasMatcherConfig = true;
        return self();
    }

    /**
     * Mounts a matcher config file from the classpath.
     *
     * @param resourcePath classpath path to the config JSON (e.g., "httptape.config.json")
     * @return this container for chaining
     * @throws IllegalArgumentException if the resource is not found on the classpath
     */
    public HttptapeContainer withMatcherConfigFromClasspath(String resourcePath) {
        withCopyFileToContainer(
                MountableFile.forClasspathResource(resourcePath),
                CONTAINER_CONFIG_PATH);
        hasMatcherConfig = true;
        return self();
    }

    /**
     * Sets the SSE replay timing mode.
     *
     * @param mode one of {@link SseTimingMode#REALTIME}, {@link SseTimingMode#INSTANT},
     *             or {@link SseTimingMode#accelerated(double)}
     * @return this container for chaining
     */
    public HttptapeContainer withSseTiming(SseTimingMode mode) {
        this.sseTiming = mode;
        return self();
    }

    /**
     * Enables or disables CORS headers (Access-Control-Allow-Origin: *) on all responses.
     *
     * @param enabled true to enable CORS
     * @return this container for chaining
     */
    public HttptapeContainer withCors(boolean enabled) {
        this.corsEnabled = enabled;
        return self();
    }

    /**
     * Adds a custom header to all replayed responses.
     *
     * @param key   header name
     * @param value header value
     * @return this container for chaining
     */
    public HttptapeContainer withReplayHeader(String key, String value) {
        replayHeaders.put(key, value);
        return self();
    }

    /**
     * Adds a fixed delay before every response.
     *
     * @param delay the delay duration
     * @return this container for chaining
     */
    public HttptapeContainer withDelay(Duration delay) {
        this.delay = delay;
        return self();
    }

    /**
     * Sets the fraction of requests that return HTTP 500.
     *
     * @param rate value between 0.0 (no errors) and 1.0 (all errors)
     * @return this container for chaining
     * @throws IllegalArgumentException if rate is outside [0.0, 1.0]
     */
    public HttptapeContainer withErrorRate(double rate) {
        if (rate < 0.0 || rate > 1.0) {
            throw new IllegalArgumentException(
                    "Error rate must be between 0.0 and 1.0, got: " + rate);
        }
        this.errorRate = rate;
        return self();
    }

    /**
     * Sets the HTTP status code returned when no fixture matches.
     *
     * @param status HTTP status code (default: 404)
     * @return this container for chaining
     */
    public HttptapeContainer withFallbackStatus(int status) {
        this.fallbackStatus = status;
        return self();
    }

    /**
     * Returns the base URL for the httptape server.
     *
     * <p>Only valid after the container has started.
     *
     * @return URL in the form {@code http://<host>:<mappedPort>}
     */
    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTPTAPE_PORT);
    }

    /**
     * Returns the mapped port for the httptape server.
     *
     * @return the host port mapped to the container's 8081
     */
    public int getPort() {
        return getMappedPort(HTTPTAPE_PORT);
    }

    /**
     * Assembles the container command from accumulated configuration state.
     * Called by Testcontainers before the container starts.
     */
    @Override
    protected void configure() {
        List<String> cmd = buildCommand();
        withCommand(cmd.toArray(new String[0]));
        super.configure();
    }

    /**
     * Builds the command-line arguments for the httptape binary.
     *
     * <p>Useful for inspecting the assembled command without starting the container.
     *
     * @return the command parts list
     */
    public List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add("serve");
        cmd.add("--fixtures");
        cmd.add(CONTAINER_FIXTURES_PATH);

        if (sseTiming != null) {
            cmd.add("--sse-timing=" + sseTiming.toCliFlag());
        }
        if (corsEnabled) {
            cmd.add("--cors");
        }
        if (delay != null && !delay.isZero()) {
            cmd.add("--delay");
            cmd.add(delay.toMillis() + "ms");
        }
        if (errorRate > 0) {
            cmd.add("--error-rate");
            cmd.add(String.valueOf(errorRate));
        }
        if (fallbackStatus >= 0) {
            cmd.add("--fallback-status");
            cmd.add(String.valueOf(fallbackStatus));
        }
        for (var entry : replayHeaders.entrySet()) {
            cmd.add("--replay-header");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }
        if (hasMatcherConfig) {
            cmd.add("--config");
            cmd.add(CONTAINER_CONFIG_PATH);
        }

        return cmd;
    }

    /**
     * Discovers JSON fixture files from the classpath, scanning the given root
     * directory recursively. Returns a map of filename to resolved path.
     *
     * <p>Handles both filesystem ({@code file:}) and JAR ({@code jar:}) classpath URLs.
     * Fails fast on filename collisions across subdirectories.
     *
     * @param classpathRoot the classpath prefix to scan (e.g., "fixtures/")
     * @return map of filename to path
     * @throws IllegalArgumentException if the classpath root is not found
     * @throws IllegalStateException    if filename collisions are detected
     */
    static Map<String, Path> discoverClasspathFixtures(String classpathRoot) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = HttptapeContainer.class.getClassLoader();
        }

        Enumeration<URL> resources;
        try {
            resources = classLoader.getResources(classpathRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan classpath root: " + classpathRoot, e);
        }

        if (!resources.hasMoreElements()) {
            throw new IllegalArgumentException(
                    "Classpath root not found: '" + classpathRoot + "'. "
                            + "Ensure the directory exists in src/test/resources/.");
        }

        Map<String, Path> fixtures = new LinkedHashMap<>();

        while (resources.hasMoreElements()) {
            URL resourceUrl = resources.nextElement();
            String protocol = resourceUrl.getProtocol();

            if ("file".equals(protocol)) {
                discoverFromFileSystem(resourceUrl, fixtures);
            } else if ("jar".equals(protocol)) {
                discoverFromJar(resourceUrl, classpathRoot, fixtures);
            }
        }

        return fixtures;
    }

    private static void discoverFromFileSystem(URL resourceUrl, Map<String, Path> fixtures) {
        Path rootPath;
        try {
            rootPath = Path.of(resourceUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid classpath URL: " + resourceUrl, e);
        }

        if (!Files.isDirectory(rootPath)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(rootPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        Path existing = fixtures.put(filename, p);
                        if (existing != null) {
                            throw new IllegalStateException(
                                    "Fixture filename collision in flat /fixtures mount: '"
                                            + filename + "' appears in both '"
                                            + existing + "' and '" + p + "'. "
                                            + "Rename one so filenames are unique across all subdirs.");
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk classpath directory: " + rootPath, e);
        }
    }

    private static void discoverFromJar(URL jarUrl, String classpathRoot, Map<String, Path> fixtures) {
        String jarPath = jarUrl.getPath();
        // jar:file:/path/to/jar.jar!/prefix/
        int separator = jarPath.indexOf("!/");
        if (separator < 0) {
            return;
        }

        String jarFilePath = jarPath.substring(5, separator); // strip "file:"
        String prefix = jarPath.substring(separator + 2); // after "!/"

        try (JarFile jar = new JarFile(jarFilePath)) {
            Enumeration<JarEntry> entries = jar.entries();
            // Create a temporary directory to extract JAR entries
            Path tempDir = Files.createTempDirectory("httptape-fixtures-");
            tempDir.toFile().deleteOnExit();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(prefix)
                        && entryName.endsWith(".json")
                        && !entry.isDirectory()) {

                    String filename = entryName.substring(entryName.lastIndexOf('/') + 1);
                    Path tempFile = tempDir.resolve(filename);

                    // Extract to temp file
                    Files.copy(jar.getInputStream(entry), tempFile);
                    tempFile.toFile().deleteOnExit();

                    Path existing = fixtures.put(filename, tempFile);
                    if (existing != null) {
                        throw new IllegalStateException(
                                "Fixture filename collision in flat /fixtures mount: '"
                                        + filename + "' appears in both '"
                                        + existing + "' and '" + tempFile + "'. "
                                        + "Rename one so filenames are unique across all subdirs.");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JAR: " + jarFilePath, e);
        }
    }
}
