package com.shareloop.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Nền cho test cần PostgreSQL thật; một container dùng chung cho mọi lớp test trong cùng JVM. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
    }
}
