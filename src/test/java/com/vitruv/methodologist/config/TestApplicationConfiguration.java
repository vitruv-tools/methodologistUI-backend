package com.vitruv.methodologist.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@TestConfiguration
public class TestApplicationConfiguration {

    @Bean
    @Primary
    public WebMvcConfigurer corsConfiguror() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedHeaders("Content-Type", "Authorization", "loggedInUserId", "Cache-Control", "Pragma", "Expires")
                        .allowedMethods("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS", "HEAD")
                        .allowedOrigins("http://localhost:8080", "http://127.0.0.1:8080");
            }
        };
    }
}
