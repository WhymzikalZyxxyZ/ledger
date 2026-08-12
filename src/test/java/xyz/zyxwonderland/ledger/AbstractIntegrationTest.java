package xyz.zyxwonderland.ledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL instance — see
 * docs/adr/004-correctness-verification.md for why a mock repository layer
 * can't stand in for this. {@code @ServiceConnection} wires Spring's
 * DataSource straight to this container; the dummy values in
 * src/test/resources/application.yml exist only so property placeholder
 * resolution doesn't fail before that wiring happens.
 *
 * <p>The container is started manually (singleton pattern) rather than
 * annotated {@code @Container} under {@code @Testcontainers}: this field is
 * static and shared via inheritance across every subclass, so a
 * per-test-class-managed lifecycle would stop it after the first test class
 * finishes, leaving every subsequent test class talking to a dead
 * container. Starting it once here and never stopping it (the JVM tears it
 * down at process exit) keeps one container alive for the whole test run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        postgres.start();
    }
}
