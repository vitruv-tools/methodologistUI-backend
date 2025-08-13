package com.vituv.methodologist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//https://stackoverflow.com/questions/64955435/flutter-web-cors-issue
//https://developer.mozilla.org/en-US/docs/Glossary/Preflight_request

@Configuration
public class ApplicationConfiguration {
    @Value("${allowed.origins}")
    private String allowedOrigins;

    @Value("${allowed.headers}")
    private String allowedHeaders;

    @Bean
    public WebMvcConfigurer corsConfiguror() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedHeaders(allowedHeaders.split(","))
                        .allowedMethods("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS", "HEAD")
                        .allowedOrigins(allowedOrigins.split(","));
            }
        };
    }
}
