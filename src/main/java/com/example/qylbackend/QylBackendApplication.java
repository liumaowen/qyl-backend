package com.example.qylbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class QylBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QylBackendApplication.class, args);
    }

    // 配置WebClient Bean
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
