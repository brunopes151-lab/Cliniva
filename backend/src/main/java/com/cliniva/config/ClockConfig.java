package com.cliniva.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, injetável para permitir testes determinísticos de
 * agenda. Zona padrão: America/Sao_Paulo (configurável via cliniva.zona).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clinivaClock(@Value("${cliniva.zona:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
