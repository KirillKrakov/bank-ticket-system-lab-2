package com.example.userservice.config;

import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({R2dbcProperties.class, FlywayProperties.class})
public class FlywayConfig {

    // Flyway автоматически конфигурируется через spring.flyway.* свойства
    // Для R2DBC нужно отдельное JDBC подключение, которое мы указали в application.yml
}