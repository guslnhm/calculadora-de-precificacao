package com.precificacao.precificacao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        // aceita vários endereços separados por vírgula (ex.: o domínio antigo e o novo na troca)
                        .allowedOrigins(java.util.Arrays.stream(allowedOrigin.split(","))
                                .map(String::strip).map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                                .filter(o -> !o.isEmpty()).toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}