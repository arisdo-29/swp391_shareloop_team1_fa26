package com.shareloop.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Seed demo chỉ được tạo khi profile "demo" bật; mọi profile khác tuyệt đối không có bean này. */
class DemoDataSeederProfileTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withUserConfiguration(DemoDataSeeder.class);
    }

    @Test
    void seeder_whenNoProfileActive_isNotCreated() {
        // given: không bật profile nào

        // when, then
        runner().run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    @Test
    void seeder_whenOnlyDevOrProdProfileActive_isNotCreated() {
        // given: profile dev và prod, không có demo

        // when, then
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", "prod"))
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    @Test
    void seeder_whenDemoProfileActive_isCreated() {
        // given: bật profile demo

        // when, then
        runner().withInitializer(context -> context.getEnvironment().setActiveProfiles("demo"))
                .run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
    }
}
