package docprocessor.processor.config;

import docprocessor.processor.analysis.DocumentAnalyzer;
import docprocessor.processor.job.JobQueue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the processor beans that are not plain components. */
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
}
