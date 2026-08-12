package xyz.zyxwonderland.ledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real PostgreSQL instance — see
 * docs/adr/004-correctness-verification.md for why a mock repository layer
 * can't stand in for this. {@code @ServiceConnection} wires Spring's
 * DataSource straight to this container; the dummy values in
 * src/test/resources/application.yml exist only so property placeholder
 * resolution doesn't fail before that wiring happens.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
