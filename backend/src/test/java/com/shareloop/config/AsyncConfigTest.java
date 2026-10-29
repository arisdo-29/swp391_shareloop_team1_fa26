package com.shareloop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AsyncConfig.class);

    @Test
    @DisplayName("Khi app.async.inline=true thì asyncExecutor là SyncTaskExecutor")
    void asyncExecutor_whenInlineTrue_isSyncTaskExecutor() {
        runner.withPropertyValues("app.async.inline=true").run(context -> {
            assertThat(context).hasBean("asyncExecutor");
            assertThat(context.getBean("asyncExecutor")).isInstanceOf(SyncTaskExecutor.class);
        });
    }

    @Test
    @DisplayName("Khi app.async.inline=false hoặc mặc định thì asyncExecutor là ThreadPoolTaskExecutor")
    void asyncExecutor_whenInlineFalseOrMissing_isThreadPoolTaskExecutor() {
        runner.run(context -> {
            assertThat(context).hasBean("asyncExecutor");
            assertThat(context.getBean("asyncExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        });

        runner.withPropertyValues("app.async.inline=false").run(context -> {
            assertThat(context).hasBean("asyncExecutor");
            assertThat(context.getBean("asyncExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
        });
    }

    @Test
    @DisplayName("Khi inline=true, exception trong phương thức @Async void không lan ra người gọi")
    void asyncVoidMethod_whenInlineTrue_exceptionDoesNotPropagateToCaller() {
        runner.withUserConfiguration(TestAsyncServiceConfig.class)
                .withPropertyValues("app.async.inline=true")
                .run(context -> {
                    TestAsyncService service = context.getBean(TestAsyncService.class);
                    assertThatCode(service::throwExceptionAsync).doesNotThrowAnyException();
                });
    }

    @Configuration
    static class TestAsyncServiceConfig {
        @Bean
        public TestAsyncService testAsyncService() {
            return new TestAsyncService();
        }
    }

    static class TestAsyncService {
        @Async
        public void throwExceptionAsync() {
            throw new RuntimeException("Simulated mail failure");
        }
    }
}
