package com.cliniva.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cliniva.tenancy.ClinicaRepository;

/** Sem ONBOARDING_PUBLICO_ATIVO, ninguém de fora cria clínica. */
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingDesligadoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Test
    void onboardingPublicoDesligadoPorPadraoResponde404ENaoCriaClinica() throws Exception {
        long antes = clinicaRepository.count();

        mockMvc.perform(post("/api/public/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nomeClinica": "Clínica de Fora",
                                  "nomeResponsavel": "Fulana",
                                  "email": "fulana@exemplo.test"
                                }
                                """))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(clinicaRepository.count()).isEqualTo(antes);
    }
}
