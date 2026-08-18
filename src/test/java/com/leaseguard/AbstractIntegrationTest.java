package com.leaseguard;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need real PostgreSQL behavior (constraints, Flyway, JPA mapping
 * validation) rather than an in-memory substitute.
 *
 * <p>The container is started once, manually, in a static initializer - the Testcontainers
 * "singleton container" pattern - and deliberately never stopped; Testcontainers' Ryuk reaper
 * removes it when the JVM exits. Every integration test class extends this one and therefore
 * shares that single running instance instead of each class starting and stopping its own,
 * which is both slower and (as observed during development) unreliable when many
 * {@code @SpringBootTest} classes run in the same Surefire fork.
 *
 * <p>Each test method still runs in its own transaction that is rolled back afterward, so
 * methods never see one another's data despite sharing the schema.
 */
@SpringBootTest
@Transactional
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    static {
        POSTGRES.start();
    }
}
