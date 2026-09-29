package com.cliniva.booking;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Rate limit simples, em memória, para o booking público (rotas abertas).
 * <p>
 * Objetivo: impedir que um script automatizado preencha a agenda da clínica.
 * Janela deslizante aproximada por contagem incremental; suficiente para o
 * free tier e sem adicionar dependência externa.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class BookingRateLimiter {

    /** Tentativas de agendamento por origem dentro da janela. */
    private static final int MAX_TENTATIVAS = 5;

    private static final Duration JANELA = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, Contador> acessos = new ConcurrentHashMap<>();

    public void registrar(String chave) {
        long agora = clock.millis();
        Contador contador = acessos.compute(chave, (k, atual) -> {
            if (atual == null || agora - atual.inicioJanela > JANELA.toMillis()) {
                return new Contador(agora, 1);
            }
            return new Contador(atual.inicioJanela, atual.tentativas.incrementAndGet());
        });

        if (contador.tentativas.get() > MAX_TENTATIVAS) {
            throw new RateLimitExcedidoException();
        }
    }

    private record Contador(long inicioJanela, AtomicInteger tentativas) {
        Contador(long inicioJanela, int tentativas) {
            this(inicioJanela, new AtomicInteger(tentativas));
        }
    }
}
