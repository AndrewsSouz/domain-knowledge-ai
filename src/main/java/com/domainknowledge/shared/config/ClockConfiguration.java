package com.domainknowledge.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio injetável: o código pede {@code clock.instant()} em vez de
 * {@code Instant.now()}, e um teste pode trocar por um relógio fixo.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
