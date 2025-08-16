package com.sun.transfer_service.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    @Bean
    public WebClient ledgerWebClient() {
        String baseUrl = "http://localhost:8081"; // Hardcoded for now

        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter((request, next) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    ClientRequest newRequest = request;
                    if (StringUtils.hasText(correlationId)) {
                        newRequest = ClientRequest.from(request)
                                .header(CorrelationIdFilter.HEADER, correlationId)
                                .build();
                    }
                    return next.exchange(newRequest);
                })
                .build();
    }

    @Bean
    public TaskExecutor batchExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(20);  // increase from 10
        ex.setMaxPoolSize(40);   // increase from 20
        ex.setQueueCapacity(200); // increase from 100
        ex.setThreadNamePrefix("batch-transfer-");
        ex.initialize();
        return ex;
    }
}
