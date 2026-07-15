package com.example;

import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
// import org.springframework.retry.annotation.EnableRetry;


@SpringBootApplication(exclude = {SpringAiRetryAutoConfiguration.class})
@EnableDiscoveryClient
// @EnableRetry
public class ChatApplication {
    public static void main(String[] args) {

        SpringApplication.run(ChatApplication.class, args);
    }
}
