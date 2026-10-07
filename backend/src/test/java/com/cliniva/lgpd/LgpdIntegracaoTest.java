package com.cliniva.lgpd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.security.jwt.SupabaseJwtVerificador;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaProvisioningService;
import com.cliniva.tenancy.Papel;
import com.cliniva.tenancy.ProfissionalRepository;
import com.cliniva.tenancy.Usuario;
import com.cliniva.tenancy.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fase 5 ponta a ponta: termo de consentimento, bloqueio do prontuário sem
 * aceite, trilha de acessos e exportação. A recusa de alterar ou apagar no
 * banco é conferida no Postgres pelo scripts/check-migrations.sh.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LgpdIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ClinicaProvisioningService provisioning;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProfissionalRepository profissionalRepository;
    @Autowired
    private ServicoRepository servicoRepository;
    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private Clock clock;

    @MockitoBean
    private SupabaseJwtVerificador verificador;

    private final ObjectMapper json = new ObjectMapper();

    private Clinica clinica;
    private String dono;
    private String recepcao;
    private String tokenAna;
    private Cliente paciente;

    @BeforeEach
    void montarClinica() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        clinica = provisioning.criarClinica("Clínica LGPD " + sufixo);
        dono = usuario(clinica, Papel.OWNER, null, "Dona");
        recepcao = usuario(clinica, Papel.RECEPCAO, null, "Recepção");
        UUID anaId = criarProfissional(dono, "Ana");
        tokenAna = usuario(clinica, Papel.PROFISSIONAL, anaId, "Ana");

        Servico servico = new Servico();
        servico.setClinica(clinica);
        servico.setNome("Fisioterapia " + sufixo);
        servico.setValor(new BigDecimal("120.00"));
        servico.setDuracaoMinutos(50);
        servicoRepository.save(servico);

        paciente = new Cliente();
        paciente.setClinica(clinica);
        paciente.setNome("Paciente Exemplo " + sufixo);
        paciente.setTelefone("5511" + numero() + numero());
        clienteRepository.save(paciente);
        mockMvc.perform(com(recepcao, post("/api/atendimentos")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"%s","dataAtendimento":"%sT09:00:00","profissionalId":"%s",
                         "servicos":[{"servicoId":"%s"}]}
                        """.formatted(paciente.getId(), proximaSegunda(), anaId, servico.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void clinicaComecaComUmTermoPadraoNaVersao1() throws Exception {
        mockMvc.perform(com(recepcao, get("/api/lgpd/termo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("13.709/2018")));
        mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/consentimentos")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.consentimentos").isEmpty());
    }

    @Test
    void semTermoAceitoNaoHaRegistroNovoNoProntuario() throws Exception {
        mockMvc.perform(com(tokenAna, novaEvolucao()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("termo")));

        JsonNode situacao = aceitar(recepcao, termoAtual());
        assertThat(situacao.get("vigente").asBoolean()).isTrue();
        assertThat(situacao.get("consentimentos").get(0).get("registradoPor").asText()).isEqualTo("Recepção");
        String registro = lerJson(mockMvc.perform(com(tokenAna, novaEvolucao())).andExpect(status().isCreated()))
                .get("id").asText();

        // Revogou: nada novo, mas o que existe continua e pode ser corrigido.
        String consentimento = situacao.get("consentimentos").get(0).get("id").asText();
        mockMvc.perform(com(recepcao, post("/api/consentimentos/" + consentimento + "/revogacao"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Pedido do paciente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente").value(false))
                .andExpect(jsonPath("$.consentimentos[0].motivoRevogacao").value("Pedido do paciente"));
        mockMvc.perform(com(tokenAna, novaEvolucao())).andExpect(status().isConflict());
        mockMvc.perform(com(tokenAna, post("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"descricao\":\"Corrigido\"},\"motivo\":\"Erro de digitação\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + paciente.getId() + "/prontuario")))
                .andExpect(status().isOk());

        // Revogar de novo não pode.
        mockMvc.perform(com(recepcao, post("/api/consentimentos/" + consentimento + "/revogacao"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"De novo\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void termoNovoVersionaEAceiteVelhoContinuaValendo() throws Exception {
        String v1 = termoAtual();
        aceitar(recepcao, v1);
        mockMvc.perform(com(recepcao, aceite(v1))).andExpect(status().isConflict());

        mockMvc.perform(com(recepcao, post("/api/lgpd/termo")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Texto da recepção\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(dono, post("/api/lgpd/termo")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Termo revisado pela clínica.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(2))
                .andExpect(jsonPath("$.publicadoPor").value("Dona"));

        // Quem aceitou a versão 1 segue atendido, mas a ficha mostra que há versão nova.
        mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/consentimentos")))
                .andExpect(jsonPath("$.termoAtual.versao").value(2))
                .andExpect(jsonPath("$.vigente").value(true))
                .andExpect(jsonPath("$.aceitouVersaoAtual").value(false))
                .andExpect(jsonPath("$.consentimentos[0].termoAtual").value(false));
        mockMvc.perform(com(tokenAna, novaEvolucao())).andExpect(status().isCreated());

        // Aceitar o texto que já saiu de vigor é recusado.
        mockMvc.perform(com(recepcao, aceite(v1))).andExpect(status().isBadRequest());
        aceitar(recepcao, termoAtual());
        mockMvc.perform(com(dono, get("/api/lgpd/termo/versoes")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].versao").value(1));
    }

    @Test
    void auditoriaGuardaQuemLeuEscreveuEExportou() throws Exception {
        aceitar(recepcao, termoAtual());
        String registro = lerJson(mockMvc.perform(com(tokenAna, novaEvolucao())).andExpect(status().isCreated()))
                .get("id").asText();
        mockMvc.perform(com(dono, get("/api/clientes/" + paciente.getId() + "/prontuario")))
                .andExpect(status().isOk());
        mockMvc.perform(com(dono, get("/api/prontuario/" + registro + "/versoes"))
                .header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(status().isOk());

        // ADMIN da plataforma em suporte: lê, e a leitura fica marcada.
        String admin = usuario(null, Papel.ADMIN, null, "Suporte");
        mockMvc.perform(com(admin, get("/api/clientes/" + paciente.getId() + "/prontuario"))
                .header("X-Clinica", clinica.getId().toString()))
                .andExpect(status().isOk());

        // Quem não pode ler não deixa rastro de leitura.
        mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/prontuario")))
                .andExpect(status().isForbidden());

        JsonNode acessos = lerJson(mockMvc.perform(com(dono, get("/api/clientes/" + paciente.getId() + "/auditoria")))
                .andExpect(status().isOk()));
        List<String> linhas = new ArrayList<>();
        acessos.forEach(a -> linhas.add(a.get("acao").asText() + " " + a.get("usuario").asText() + " "
                + a.get("papel").asText() + " " + a.get("modoSuporte").asBoolean()));
        assertThat(linhas).containsExactly(
                "VER_PRONTUARIO Suporte ADMIN true",
                "VER_HISTORICO Dona OWNER false",
                "VER_PRONTUARIO Dona OWNER false",
                "CRIAR_REGISTRO Ana PROFISSIONAL false",
                "REGISTRAR_CONSENTIMENTO Recepção RECEPCAO false");
        assertThat(acessos.get(1).get("registroId").asText()).isEqualTo(registro);
        assertThat(acessos.get(1).get("ip").asText()).isEqualTo("203.0.113.9");

        mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/auditoria")))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + paciente.getId() + "/auditoria")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportacaoTrazTudoDoPacienteESoOAdministradorExporta() throws Exception {
        aceitar(recepcao, termoAtual());
        String registro = lerJson(mockMvc.perform(com(tokenAna, novaEvolucao())).andExpect(status().isCreated()))
                .get("id").asText();
        mockMvc.perform(com(tokenAna, post("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"descricao\":\"Corrigido\"},\"motivo\":\"Erro de digitação\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(com(dono, get("/api/clientes/" + paciente.getId() + "/exportacao")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.geradoPor").value("Dona"))
                .andExpect(jsonPath("$.cadastro.nome").value(paciente.getNome()))
                .andExpect(jsonPath("$.atendimentos.length()").value(1))
                .andExpect(jsonPath("$.prontuario.length()").value(1))
                .andExpect(jsonPath("$.prontuario[0].versoes.length()").value(2))
                .andExpect(jsonPath("$.prontuario[0].versoes[1].conteudo.descricao").value("Liberação miofascial"))
                .andExpect(jsonPath("$.consentimentos.length()").value(1))
                .andExpect(jsonPath("$.termosAceitos[0].versao").value(1));

        mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/exportacao")))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + paciente.getId() + "/exportacao")))
                .andExpect(status().isForbidden());
        String admin = usuario(null, Papel.ADMIN, null, "Suporte");
        mockMvc.perform(com(admin, get("/api/clientes/" + paciente.getId() + "/exportacao"))
                .header("X-Clinica", clinica.getId().toString()))
                .andExpect(status().isForbidden());

        JsonNode acessos = lerJson(mockMvc.perform(com(dono, get("/api/clientes/" + paciente.getId() + "/auditoria"))));
        assertThat(acessos.get(0).get("acao").asText()).isEqualTo("EXPORTAR_DADOS");
    }

    @Test
    void adminDaPlataformaNaoRegistraAceiteEOutraClinicaNaoVe() throws Exception {
        String admin = usuario(null, Papel.ADMIN, null, "Suporte");
        mockMvc.perform(com(admin, aceite(termoAtual())).header("X-Clinica", clinica.getId().toString()))
                .andExpect(status().isForbidden());

        Clinica outra = provisioning.criarClinica("Outra LGPD " + UUID.randomUUID().toString().substring(0, 8));
        String donoDaOutra = usuario(outra, Papel.OWNER, null, "Outra");
        mockMvc.perform(com(donoDaOutra, get("/api/clientes/" + paciente.getId() + "/consentimentos")))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(donoDaOutra, get("/api/clientes/" + paciente.getId() + "/auditoria")))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(donoDaOutra, get("/api/clientes/" + paciente.getId() + "/exportacao")))
                .andExpect(status().isNotFound());
    }

    @Test
    void pacienteSemProntuarioPodeSerExcluidoMesmoComTermoAceito() throws Exception {
        Cliente avulso = new Cliente();
        avulso.setClinica(clinica);
        avulso.setNome("Paciente Avulso");
        avulso.setTelefone("5511" + numero() + numero());
        clienteRepository.save(avulso);
        mockMvc.perform(com(recepcao, post("/api/clientes/" + avulso.getId() + "/consentimentos"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"termoId\":\"" + termoAtual() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(com(dono, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/clientes/" + avulso.getId())))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ apoio

    private String termoAtual() throws Exception {
        return lerJson(mockMvc.perform(com(recepcao, get("/api/lgpd/termo"))).andExpect(status().isOk()))
                .get("id").asText();
    }

    private MockHttpServletRequestBuilder aceite(String termoId) {
        return post("/api/clientes/" + paciente.getId() + "/consentimentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"termoId\":\"" + termoId + "\"}");
    }

    private JsonNode aceitar(String token, String termoId) throws Exception {
        return lerJson(mockMvc.perform(com(token, aceite(termoId))).andExpect(status().isOk()));
    }

    private MockHttpServletRequestBuilder novaEvolucao() throws Exception {
        JsonNode modelos = lerJson(mockMvc.perform(com(dono, get("/api/fichas/modelos"))).andExpect(status().isOk()));
        String modeloId = null;
        for (JsonNode m : modelos) {
            if (m.get("nome").asText().equals("Evolução da sessão")) {
                modeloId = m.get("id").asText();
            }
        }
        return post("/api/clientes/" + paciente.getId() + "/prontuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modeloId\":\"" + modeloId + "\",\"conteudo\":{\"descricao\":\"Liberação miofascial\"}}");
    }

    private String usuario(Clinica clinicaDoUsuario, Papel papel, UUID profissionalId, String nome) {
        String token = "tok-" + UUID.randomUUID();
        Usuario usuario = new Usuario();
        usuario.setClinica(clinicaDoUsuario);
        usuario.setPapel(papel);
        usuario.setNome(nome);
        usuario.setEmail(token + "@exemplo.test");
        usuario.setSupabaseUserId(token);
        usuario.setAtivo(true);
        if (profissionalId != null) {
            usuario.setProfissional(profissionalRepository.findById(profissionalId).orElseThrow());
        }
        usuarioRepository.save(usuario);
        io.jsonwebtoken.Claims claims = org.mockito.Mockito.mock(io.jsonwebtoken.Claims.class);
        org.mockito.Mockito.when(claims.getSubject()).thenReturn(token);
        org.mockito.Mockito.when(verificador.verificar(token)).thenReturn(claims);
        return token;
    }

    private UUID criarProfissional(String token, String nome) throws Exception {
        JsonNode criado = lerJson(mockMvc.perform(com(token, post("/api/profissionais"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()));
        return UUID.fromString(criado.get("id").asText());
    }

    private MockHttpServletRequestBuilder com(String token, MockHttpServletRequestBuilder requisicao) {
        return requisicao.header("Authorization", "Bearer " + token);
    }

    private JsonNode lerJson(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private LocalDate proximaSegunda() {
        LocalDate dia = LocalDate.now(clock).plusDays(1);
        while (dia.getDayOfWeek() != DayOfWeek.MONDAY) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private static String numero() {
        return String.valueOf(1000 + (int) (Math.random() * 9000));
    }
}
