package com.cliniva.prontuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
 * Prontuário ponta a ponta: perfis, versões e modelos, com rotas e H2
 * reais. A recusa de UPDATE/DELETE no banco é conferida no Postgres pelo
 * scripts/check-migrations.sh.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProntuarioIntegracaoTest {

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
    private String tokenBia;
    private UUID anaId;
    private Servico servico;
    private Cliente pacienteDaAna;
    private Cliente outroPaciente;

    @BeforeEach
    void montarClinica() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        clinica = provisioning.criarClinica("Clínica Prontuário " + sufixo);
        dono = usuario(clinica, Papel.OWNER, null);
        recepcao = usuario(clinica, Papel.RECEPCAO, null);
        anaId = criarProfissional(dono, "Ana");
        UUID biaId = criarProfissional(dono, "Bia");
        tokenAna = usuario(clinica, Papel.PROFISSIONAL, anaId);
        tokenBia = usuario(clinica, Papel.PROFISSIONAL, biaId);

        servico = new Servico();
        servico.setClinica(clinica);
        servico.setNome("Fisioterapia " + sufixo);
        servico.setValor(new BigDecimal("120.00"));
        servico.setDuracaoMinutos(50);
        servicoRepository.save(servico);

        pacienteDaAna = paciente("Paciente A " + sufixo);
        outroPaciente = paciente("Paciente B " + sufixo);
        // Registro clínico exige termo aceito (Fase 5); as regras dele estão no LgpdIntegracaoTest.
        aceitarTermo(pacienteDaAna);
        aceitarTermo(outroPaciente);
        mockMvc.perform(com(recepcao, post("/api/atendimentos")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"%s","dataAtendimento":"%sT09:00:00","profissionalId":"%s",
                         "servicos":[{"servicoId":"%s"}]}
                        """.formatted(pacienteDaAna.getId(), proximaSegunda(), anaId, servico.getId())))
                .andExpect(status().isCreated());
    }

    @Test
    void clinicaComecaComOsModelosPadrao() throws Exception {
        JsonNode modelos = lerJson(mockMvc.perform(com(tokenAna, get("/api/fichas/modelos")))
                .andExpect(status().isOk()));
        assertThat(modelos).extracting(m -> m.get("nome").asText()).contains(
                "Anamnese estética", "Anamnese fisioterapêutica", "Avaliação fisioterapêutica",
                "Plano terapêutico", "Evolução da sessão", "Reavaliação");
        assertThat(modelos).allMatch(m -> m.get("versao").asInt() == 1);
    }

    @Test
    void profissionalRegistraCorrigeEOHistoricoGuardaTodasAsVersoes() throws Exception {
        String modelo = modeloId("Anamnese fisioterapêutica");
        JsonNode criado = lerJson(mockMvc.perform(com(tokenAna,
                post("/api/clientes/" + pacienteDaAna.getId() + "/prontuario"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"modeloId":"%s","conteudo":{"queixa":"Dor lombar ao sentar","dor":7,
                         "inicio_sintomas":"2026-09-01","local_dor":""}}
                        """.formatted(modelo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ANAMNESE"))
                .andExpect(jsonPath("$.versoes").value(1))
                .andExpect(jsonPath("$.podeCorrigir").value(true))
                .andExpect(jsonPath("$.atual.conteudo.dor").value(7))
                // Campo vazio não é gravado.
                .andExpect(jsonPath("$.atual.conteudo.local_dor").doesNotExist()));
        String registro = criado.get("id").asText();

        // Corrigir sem motivo não pode.
        mockMvc.perform(com(tokenAna, post("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"queixa\":\"Dor lombar\",\"dor\":6},\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(com(tokenAna, post("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"queixa\":\"Dor lombar ao sentar\",\"dor\":6},"
                        + "\"motivo\":\"Nota de dor digitada errado\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versoes").value(2))
                .andExpect(jsonPath("$.atual.numero").value(2))
                .andExpect(jsonPath("$.atual.conteudo.dor").value(6));

        JsonNode historico = lerJson(mockMvc.perform(com(dono, get("/api/prontuario/" + registro + "/versoes")))
                .andExpect(status().isOk()));
        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).get("motivo").asText()).isEqualTo("Nota de dor digitada errado");
        assertThat(historico.get(1).get("conteudo").get("dor").asInt()).isEqualTo(7);
        assertThat(historico.get(1).get("motivo").isNull()).isTrue();
    }

    @Test
    void naoHaRotaParaApagarOuEditarNoLugar() throws Exception {
        String registro = registrarEvolucao(tokenAna, pacienteDaAna).get("id").asText();
        mockMvc.perform(com(dono, delete("/api/prontuario/" + registro)))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(404, 405));
        mockMvc.perform(com(dono, put("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    void recepcaoNaoVeConteudoClinico() throws Exception {
        String registro = registrarEvolucao(tokenAna, pacienteDaAna).get("id").asText();
        mockMvc.perform(com(recepcao, get("/api/clientes/" + pacienteDaAna.getId() + "/prontuario")))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(recepcao, get("/api/prontuario/" + registro + "/versoes")))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(recepcao, get("/api/fichas/modelos")))
                .andExpect(status().isForbidden());
    }

    @Test
    void profissionalSoNoPacienteQueAtende() throws Exception {
        // Bia não atende a paciente da Ana: não lê nem escreve.
        mockMvc.perform(com(tokenBia, get("/api/clientes/" + pacienteDaAna.getId() + "/prontuario")))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(tokenBia, post("/api/clientes/" + pacienteDaAna.getId() + "/prontuario"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(evolucaoJson(modeloId("Evolução da sessão"))))
                .andExpect(status().isNotFound());
        // Ana também não, no paciente que não atende.
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + outroPaciente.getId() + "/prontuario")))
                .andExpect(status().isNotFound());
    }

    @Test
    void soQuemRegistrouCorrige() throws Exception {
        String registro = registrarEvolucao(tokenAna, pacienteDaAna).get("id").asText();
        // O administrador lê tudo, mas não tem cadastro de profissional: não escreve.
        mockMvc.perform(com(dono, get("/api/clientes/" + pacienteDaAna.getId() + "/prontuario")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].podeCorrigir").value(false));
        mockMvc.perform(com(dono, post("/api/prontuario/" + registro + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"descricao\":\"x\"},\"motivo\":\"teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostasSaoConferidasContraOModelo() throws Exception {
        String modelo = modeloId("Anamnese fisioterapêutica");
        String url = "/api/clientes/" + pacienteDaAna.getId() + "/prontuario";
        // Obrigatória vazia, escala fora de 0 a 10, pergunta que não existe.
        for (String conteudo : new String[] { "{\"dor\":3}", "{\"queixa\":\"x\",\"dor\":11}",
                "{\"queixa\":\"x\",\"inventada\":\"y\"}", "{\"queixa\":\"x\",\"inicio_sintomas\":\"31/02\"}" }) {
            mockMvc.perform(com(tokenAna, post(url)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"modeloId\":\"" + modelo + "\",\"conteudo\":" + conteudo + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void editarModeloCriaVersaoNovaERegistroAntigoContinuaNaVersaoQueUsou() throws Exception {
        JsonNode original = modelo("Evolução da sessão");
        String familia = original.get("familiaId").asText();
        JsonNode registro = registrarEvolucao(tokenAna, pacienteDaAna);

        String novaVersao = """
                {"nome":"Evolução da sessão","area":"GERAL","tipo":"EVOLUCAO","campos":[
                  {"id":"descricao","rotulo":"O que foi feito","tipo":"TEXTO_LONGO","obrigatorio":true},
                  {"id":"conduta","rotulo":"Conduta","tipo":"OPCOES","obrigatorio":false,
                   "opcoes":["Manter","Ajustar","Alta"]}]}
                """;
        // Recepção e profissional não editam modelos.
        mockMvc.perform(com(tokenAna, post("/api/fichas/modelos/" + familia + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON).content(novaVersao))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(dono, post("/api/fichas/modelos/" + familia + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON).content(novaVersao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versao").value(2));

        // A lista mostra só a versão atual de cada modelo.
        assertThat(modelo("Evolução da sessão").get("versao").asInt()).isEqualTo(2);
        // Registrar com a versão antiga é recusado.
        mockMvc.perform(com(tokenAna, post("/api/clientes/" + pacienteDaAna.getId() + "/prontuario"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(evolucaoJson(original.get("id").asText())))
                .andExpect(status().isBadRequest());

        // O registro feito antes continua com o formulário da versão 1, e a
        // correção dele também.
        mockMvc.perform(com(tokenAna, post("/api/prontuario/" + registro.get("id").asText() + "/versoes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":{\"descricao\":\"Liberação miofascial\",\"dor\":2},"
                        + "\"motivo\":\"Faltou a nota de dor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.atual.modelo.versao").value(1));
    }

    @Test
    void modeloDesativadoSaiDaListaMasContinuaEmTodos() throws Exception {
        String familia = modelo("Reavaliação").get("familiaId").asText();
        mockMvc.perform(com(dono, patch("/api/fichas/modelos/" + familia + "/ativo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk());
        JsonNode ativos = lerJson(mockMvc.perform(com(tokenAna, get("/api/fichas/modelos"))));
        assertThat(ativos).extracting(m -> m.get("nome").asText()).doesNotContain("Reavaliação");
        JsonNode todos = lerJson(mockMvc.perform(com(dono, get("/api/fichas/modelos?todos=true"))));
        assertThat(todos).extracting(m -> m.get("nome").asText()).contains("Reavaliação");
    }

    @Test
    void pacienteComProntuarioNaoPodeSerExcluido() throws Exception {
        registrarEvolucao(tokenAna, pacienteDaAna);
        mockMvc.perform(com(dono, delete("/api/clientes/" + pacienteDaAna.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void outraClinicaNaoVeOProntuario() throws Exception {
        registrarEvolucao(tokenAna, pacienteDaAna);
        Clinica outra = provisioning.criarClinica("Outra " + UUID.randomUUID().toString().substring(0, 8));
        String donoDaOutra = usuario(outra, Papel.OWNER, null);
        mockMvc.perform(com(donoDaOutra, get("/api/clientes/" + pacienteDaAna.getId() + "/prontuario")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ apoio

    private JsonNode registrarEvolucao(String token, Cliente paciente) throws Exception {
        return lerJson(mockMvc.perform(com(token, post("/api/clientes/" + paciente.getId() + "/prontuario"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(evolucaoJson(modeloId("Evolução da sessão"))))
                .andExpect(status().isCreated()));
    }

    private void aceitarTermo(Cliente paciente) throws Exception {
        String termo = lerJson(mockMvc.perform(com(recepcao, get("/api/lgpd/termo")))
                .andExpect(status().isOk())).get("id").asText();
        mockMvc.perform(com(recepcao, post("/api/clientes/" + paciente.getId() + "/consentimentos"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"termoId\":\"" + termo + "\"}"))
                .andExpect(status().isOk());
    }

    private static String evolucaoJson(String modeloId) {
        return "{\"modeloId\":\"" + modeloId + "\",\"conteudo\":{\"descricao\":\"Liberação miofascial\"}}";
    }

    private String modeloId(String nome) throws Exception {
        return modelo(nome).get("id").asText();
    }

    private JsonNode modelo(String nome) throws Exception {
        JsonNode modelos = lerJson(mockMvc.perform(com(dono, get("/api/fichas/modelos")))
                .andExpect(status().isOk()));
        for (JsonNode m : modelos) {
            if (m.get("nome").asText().equals(nome)) {
                return m;
            }
        }
        throw new AssertionError("modelo não listado: " + nome);
    }

    private Cliente paciente(String nome) {
        Cliente cliente = new Cliente();
        cliente.setClinica(clinica);
        cliente.setNome(nome);
        cliente.setTelefone("5511" + numero() + numero());
        return clienteRepository.save(cliente);
    }

    private String usuario(Clinica clinicaDoUsuario, Papel papel, UUID profissionalId) {
        String token = "tok-" + UUID.randomUUID();
        Usuario usuario = new Usuario();
        usuario.setClinica(clinicaDoUsuario);
        usuario.setPapel(papel);
        usuario.setNome(papel.name());
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
