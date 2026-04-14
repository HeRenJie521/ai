package com.eaju.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.eaju.ai.persistence.repository")
@EntityScan(basePackages = "com.eaju.ai.persistence.entity")
public class EajuAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EajuAiApplication.class, args);
    }
}
