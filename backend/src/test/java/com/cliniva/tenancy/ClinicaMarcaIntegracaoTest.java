package com.cliniva.tenancy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ClinicaMarcaIntegracaoTest {

    private static final String LOGO = "data:image/png;base64," + "A".repeat(2000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Test
    void marcaPublicaPeloSlugDevolveNomeELogo() throws Exception {
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica Marca Teste");
        clinica.setSlug("clinica-marca-teste");
        clinica.setLogoDataUrl(LOGO);
        clinicaRepository.save(clinica);

        mockMvc.perform(get("/api/public/marca").param("slug", "clinica-marca-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Clínica Marca Teste"))
                .andExpect(jsonPath("$.logoDataUrl").value(LOGO));
    }

    @Test
    void marcaPublicaSemSlugNuncaFalha() throws Exception {
        mockMvc.perform(get("/api/public/marca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").isNotEmpty());
    }

    @Test
    void alterarMarcaExigeLogin() throws Exception {
        mockMvc.perform(put("/api/clinica/marca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Invasora\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clinica/marca"))
                .andExpect(status().isUnauthorized());
    }
}
