package com.orbtl.relay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(1024 * 1024)) // 1MB buffer
            .filter(logRequest());
    }
    
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            log.info("All headers being sent:");
            clientRequest.headers().forEach((name, values) -> {
                values.forEach(value -> {
                    if (name.equalsIgnoreCase("X-API-Key")) {
                        log.info("  {} = {}", name, value.substring(0, Math.min(10, value.length())) + "...");
                    } else {
                        log.info("  {} = {}", name, value);
                    }
                });
            });
            return Mono.just(clientRequest);
        });
    }
}