package com.bank.core.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta un PostgreSQL efímero con Testcontainers y compartido por todas las
 * pruebas de integración del JVM. Flyway aplica las migraciones al arrancar el
 * contexto de Spring.
 */
@SpringBootTest
public abstract class AbstractPostgresIntegrationTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("core_banking")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiarBaseDeDatos() {
        jdbcTemplate.execute("TRUNCATE TABLE transactions, accounts, users RESTART IDENTITY CASCADE");
    }
}
