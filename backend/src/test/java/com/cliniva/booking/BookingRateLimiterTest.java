package com.cliniva.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class BookingRateLimiterTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    /** Clock mutável para avançar o tempo dentro do teste. */
    private static final class RelogioMutavel extends Clock {
        private Instant agora = Instant.parse("2026-09-29T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZONA;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Test
    void devePermitirAteCincoTentativasNaJanela() {
        BookingRateLimiter limiter = new BookingRateLimiter(new RelogioMutavel());

        for (int i = 1; i <= 5; i++) {
            limiter.registrar("clinica-a|5511999999999|1.1.1.1");
        }

        assertThatThrownBy(() -> limiter.registrar("clinica-a|5511999999999|1.1.1.1"))
                .isInstanceOf(RateLimitExcedidoException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void naoDeveBloquearOutrasOrigens() {
        BookingRateLimiter limiter = new BookingRateLimiter(new RelogioMutavel());

        for (int i = 1; i <= 5; i++) {
            limiter.registrar("clinica-a|5511999999999|1.1.1.1");
        }

        // outro telefone/IP continua passando
        limiter.registrar("clinica-a|5511888888888|2.2.2.2");
    }

    @Test
    void deveLiberarNovamenteDepoisDaJanelaDeDezMinutos() {
        RelogioMutavel relogio = new RelogioMutavel();
        BookingRateLimiter limiter = new BookingRateLimiter(relogio);

        for (int i = 1; i <= 5; i++) {
            limiter.registrar("clinica-a|5511999999999|1.1.1.1");
        }
        assertThatThrownBy(() -> limiter.registrar("clinica-a|5511999999999|1.1.1.1"))
                .isInstanceOf(RateLimitExcedidoException.class);

        relogio.avancar(Duration.ofMinutes(11));

        // janela nova: volta a permitir
        limiter.registrar("clinica-a|5511999999999|1.1.1.1");
    }
}
