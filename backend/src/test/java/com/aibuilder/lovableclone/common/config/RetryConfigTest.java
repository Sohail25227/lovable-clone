package com.aibuilder.lovableclone.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Model retry poori tarah configuration hai, aur uski failure chup hai: galat prefix pe
 * likho to app theek boot hota hai, properties bind hoti dikhti hain, aur ek bhi retry
 * nahi hota.
 *
 * Yeh isi project mein ho chuka hai. spring.ai.retry.* set kiya gaya tha, par Spring AI 2.0
 * ka OpenAI path openai-java SDK (OkHttp) se jata hai — OpenAiChatAutoConfiguration ka
 * openAiChatModel(...) na RetryTemplate leta hai na ResponseErrorHandler. Woh block
 * inert tha; asli knob spring.ai.openai.max-retries hai. Isliye yeh test wahi pin karta hai.
 *
 * Test asli application.yml load karta hai (ConfigDataApplicationContextInitializer), isliye
 * yeh Spring ke binder ko nahi, hamari config ko pin karta hai. Na DB chahiye na Groq key.
 */
class RetryConfigTest {

    @Configuration
    @EnableConfigurationProperties(OpenAiCommonProperties.class)
    static class OpenAiPropertiesOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(OpenAiPropertiesOnly.class)
            // api-key yml mein ${GROQ_API_KEY} hai. Yeh higher-precedence value usse
            // resolve hone se bachati hai, taaki test ko asli key ki zarurat na pade
            .withPropertyValues("spring.ai.openai.api-key=not-a-real-key");

    // SDK default 3 attempts x 60s timeout hai, matlab worst case ~3 min ek request thread ka.
    // generate synchronous hai aur uske upar validation ka apna retry bhi hai, isliye
    // yahan budget kasa hua hona chahiye — warna ek slow model do minute ka page load ban jata
    @Test
    void theModelRetryBudgetIsBounded() {
        runner.run(context -> {
            OpenAiCommonProperties properties = context.getBean(OpenAiCommonProperties.class);

            assertThat(properties.getMaxRetries()).isEqualTo(2);
            assertThat(properties.getTimeout()).isLessThanOrEqualTo(Duration.ofSeconds(45));
        });
    }

    // Base URL Groq pe hona chahiye. Yeh hat jaye to calls chupchaap OpenAI ko jaati hain,
    // jahan yeh key kaam nahi karti — aur 401 ki wajah dhoondhne mein waqt jata hai
    @Test
    void theModelPointsAtGroq() {
        runner.run(context -> assertThat(context.getBean(OpenAiCommonProperties.class).getBaseUrl())
                .isEqualTo("https://api.groq.com/openai/v1"));
    }
}
