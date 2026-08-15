package com.aibuilder.lovableclone.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryTemplate;

// Retry poori tarah configuration hai, code nahi — aur uski dono failure modes chup hain:
// dependency gayab ho to retry gayab, aur budget khula ho to thread mint bhar block.
// Yeh test asli application.yml load karta hai (ConfigDataApplicationContextInitializer),
// isliye yeh Spring ke binder ko nahi, hamari config ko pin karta hai. Na DB chahiye na key
class RetryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class));

    // SpringAiRetryAutoConfiguration @ConditionalOnClass(RetryUtils) hai, aur RetryUtils
    // spring-ai-retry mein rehta hai — jo starter ka optional dependency hai. Woh jar hataao
    // to yeh condition chupchaap fail hoti hai, app theek boot hota hai, aur ek bhi retry
    // nahi hota. Bean ka hona hi is baat ka saboot hai ki jar classpath pe hai
    @Test
    void retryIsWiredAtAll() {
        runner.run(context -> assertThat(context).hasSingleBean(RetryTemplate.class));
    }

    // Defaults 10 attempts, 2s initial, x5, 3m cap hain — worst case ek request thread
    // ~19 min tak baithi rehti. Generate synchronous hai, isliye budget bandha hua chahiye
    @Test
    void theRetryBudgetIsBounded() {
        runner.run(context -> {
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);

            assertThat(properties.getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getBackoff().getMaxInterval())
                    .isLessThanOrEqualTo(Duration.ofSeconds(5));
        });
    }

    // 429 4xx hai aur on-client-errors default false hai, to bina is list ke rate limit
    // NonTransient ban ke bina retry fail hota. Free tier pe 429 aam hai, isliye yeh
    // ek line hi retry ko kaam ki cheez banati hai
    @Test
    void rateLimitsAreTreatedAsTransient() {
        runner.run(context -> {
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);

            assertThat(properties.getOnHttpCodes()).contains(429);
            // Baaki 4xx retry na ho: galat API key ya bekaar request dobara bhejna bekaar hai
            assertThat(properties.isOnClientErrors()).isFalse();
        });
    }
}
