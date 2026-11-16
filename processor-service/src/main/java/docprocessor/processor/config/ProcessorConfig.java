package docprocessor.processor.config;

import docprocessor.processor.analysis.DocumentAnalyzer;
import docprocessor.processor.events.JobEventPublisher;
import docprocessor.processor.job.JobQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the processor beans that are not plain components. */
@Slf4j
@Configuration
@EnableConfigurationProperties(ProcessorProperties.class)
public class ProcessorConfig {

    @Bean
    DocumentAnalyzer documentAnalyzer() {
        return new DocumentAnalyzer();
    }

    @Bean
    JobQueue jobQueue(ProcessorProperties properties) {
        return new JobQueue(properties.workers(), properties.queueCapacity());
    }

    // Logging-only publisher; Task 6 replaces it with the WebSocket publisher.
    @Bean
    JobEventPublisher jobEventPublisher() {
        return event -> log.info("Job event {} for job {}", event.type(), event.jobId());
    }
}
