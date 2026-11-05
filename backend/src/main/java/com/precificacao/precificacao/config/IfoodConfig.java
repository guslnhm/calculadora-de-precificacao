package com.precificacao.precificacao.config;

import com.precificacao.precificacao.ifood.IfoodProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IfoodProperties.class)
public class IfoodConfig {
}
