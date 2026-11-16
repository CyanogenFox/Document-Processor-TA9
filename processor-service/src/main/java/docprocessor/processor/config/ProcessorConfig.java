package docprocessor.processor.config;

import docprocessor.processor.analysis.DocumentAnalyzer;
import docprocessor.processor.service.JobQueue;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Wires the processor beans that are not plain components. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ProcessorProperties.class)
public class ProcessorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DocumentAnalyzer documentAnalyzer() {
        return new DocumentAnalyzer();
    }

    @Bean
    JobQueue jobQueue(ProcessorProperties properties) {
        return new JobQueue(properties.workers(), properties.queueCapacity());
    }
}
