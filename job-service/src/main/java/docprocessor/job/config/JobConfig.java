package docprocessor.job.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/** Wires the clock and switches on properties, retry and scheduling support. */
@Configuration
@EnableConfigurationProperties(JobProperties.class)
@EnableResilientMethods
@EnableScheduling
public class JobConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Built from the auto-configured builder so the spring.http.clients timeouts apply. */
    @Bean
    RestClient processorRestClient(RestClient.Builder builder, JobProperties properties) {
        return builder.baseUrl(properties.processor().baseUrl().toString()).build();
    }
}
