package com.cognition.est;

import com.cognition.est.app.config.EstProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enablement Session Tracker API. */
@SpringBootApplication
@EnableConfigurationProperties(EstProperties.class)
@EnableScheduling
public class EstApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstApiApplication.class, args);
    }
}
