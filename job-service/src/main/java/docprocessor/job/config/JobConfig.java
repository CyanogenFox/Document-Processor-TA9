package docprocessor.job.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    /** Timeouts are set in code, not application.yml, because the service must run without a config file. */
    @Bean
    RestClient processorRestClient(RestClient.Builder builder, JobProperties properties) {
        JobProperties.Processor processor = properties.processor();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(processor.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(processor.readTimeout());
        return builder.baseUrl(processor.baseUrl().toString()).requestFactory(factory).build();
    }
}
