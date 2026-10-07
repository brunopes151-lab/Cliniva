package com.cliniva.pacote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Séries e pacotes ponta a ponta: rotas, perfis e banco H2 reais. Só a
 * assinatura do token é simulada (o token é o supabase_user_id).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeriePacoteIntegracaoTest {

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
    private UUID anaId;
    private Servico servico;
    private Cliente paciente;

    @BeforeEach
    void montarClinica() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        clinica = provisioning.criarClinica("Clínica Séries " + sufixo);
        dono = usuario(clinica, Papel.OWNER, null);
        recepcao = usuario(clinica, Papel.RECEPCAO, null);
        anaId = criarProfissional(dono, "Ana");
        tokenAna = usuario(clinica, Papel.PROFISSIONAL, anaId);

        servico = new Servico();
        servico.setClinica(clinica);
        servico.setNome("Fisioterapia " + sufixo);
        servico.setValor(new BigDecimal("120.00"));
        servico.setDuracaoMinutos(50);
        servicoRepository.save(servico);

        paciente = new Cliente();
        paciente.setClinica(clinica);
        paciente.setNome("Paciente Série " + sufixo);
        paciente.setTelefone("5511" + numero() + numero());
        clienteRepository.save(paciente);
    }

    @Test
    void serieSemanalPulaOHorarioOcupadoEMarcaAQuantidadePedida() throws Exception {
        LocalDateTime primeira = proximaSegunda().atTime(10, 0);
        // A segunda semana já está ocupada na agenda da Ana.
        agendarAvulso(primeira.plusWeeks(1)).andExpect(status().isCreated());

        JsonNode previa = lerJson(mockMvc.perform(com(recepcao, post("/api/atendimentos/series/previa"))
                .contentType(MediaType.APPLICATION_JSON).content(serie(primeira, "SEMANAL", 4)))
                .andExpect(status().isOk()));
        assertThat(previa.get("disponiveis").asInt()).isEqualTo(4);
        assertThat(previa.get("puladas").asInt()).isEqualTo(1);
        assertThat(previa.get("ocorrencias").get(1).get("motivo").asText()).contains("ocupado");

        JsonNode criada = lerJson(mockMvc.perform(com(recepcao, post("/api/atendimentos/series"))
                .contentType(MediaType.APPLICATION_JSON).content(serie(primeira, "SEMANAL", 4)))
                .andExpect(status().isCreated()));
        assertThat(criada.get("criados")).hasSize(4);
        assertThat(criada.get("puladas")).hasSize(1);

        // A agenda do dia mostra a sessão ligada à série.
        JsonNode agenda = lerJson(mockMvc.perform(com(recepcao, get("/api/agenda")
                .param("data", primeira.toLocalDate().toString()))).andExpect(status().isOk()));
        assertThat(agenda.get(0).get("serieId").asText()).isEqualTo(criada.get("serieId").asText());
    }

    @Test
    void serieAlemDos30DiasCabeNaJanelaDaEquipe() throws Exception {
        LocalDateTime primeira = proximaSegunda().atTime(9, 0);
        JsonNode criada = lerJson(mockMvc.perform(com(recepcao, post("/api/atendimentos/series"))
                .contentType(MediaType.APPLICATION_JSON).content(serie(primeira, "SEMANAL", 10)))
                .andExpect(status().isCreated()));
        assertThat(criada.get("criados")).hasSize(10);
    }

    @Test
    void cancelarUmaETodasAsSeguintes() throws Exception {
        LocalDateTime primeira = proximaSegunda().atTime(15, 0);
        JsonNode criada = lerJson(mockMvc.perform(com(recepcao, post("/api/atendimentos/series"))
                .contentType(MediaType.APPLICATION_JSON).content(serie(primeira, "SEMANAL", 4)))
                .andExpect(status().isCreated()));
        String segunda = criada.get("criados").get(1).get("id").asText();
        String terceira = criada.get("criados").get(2).get("id").asText();

        // Só uma: a segunda sessão.
        mudarStatus(segunda, "CANCELADO").andExpect(status().isOk());
        // Esta e as seguintes, a partir da terceira.
        mockMvc.perform(com(recepcao, post("/api/atendimentos/" + terceira + "/cancelar-seguintes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelados").value(2));

        JsonNode lista = lerJson(mockMvc.perform(com(recepcao, get("/api/atendimentos")
                .param("clienteId", paciente.getId().toString()))).andExpect(status().isOk()));
        assertThat(lista).extracting(a -> a.get("status").asText())
                .containsExactlyInAnyOrder("AGENDADO", "CANCELADO", "CANCELADO", "CANCELADO");
    }

    @Test
    void pacoteBaixaAoConcluirEVoltaQuandoOConcluidoECancelado() throws Exception {
        String modelo = criarModelo(5, 90);
        String vendido = lerJson(mockMvc.perform(com(recepcao, post("/api/clientes/" + paciente.getId() + "/pacotes"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pacoteId\":\"" + modelo + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(5))).get("id").asText();

        String atendimento = lerJson(agendarAvulso(proximaSegunda().atTime(11, 0)).andExpect(status().isCreated()))
                .get("id").asText();

        mudarStatus(atendimento, "CONCLUIDO").andExpect(status().isOk());
        assertThat(saldo(vendido)).isEqualTo(4);

        mudarStatus(atendimento, "CANCELADO").andExpect(status().isOk());
        assertThat(saldo(vendido)).isEqualTo(5);

        JsonNode pacote = pacoteDoPaciente(vendido);
        assertThat(pacote.get("movimentos")).extracting(m -> m.get("tipo").asText())
                .containsExactlyInAnyOrder("BAIXA", "ESTORNO");
    }

    @Test
    void pacoteVencidoNaoDaBaixa() throws Exception {
        String modelo = criarModelo(5, 30);
        String dataAntiga = LocalDate.now(clock).minusDays(60).toString();
        String vendido = lerJson(mockMvc.perform(com(recepcao, post("/api/clientes/" + paciente.getId() + "/pacotes"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pacoteId\":\"" + modelo + "\",\"dataCompra\":\"" + dataAntiga + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("VENCIDO"))).get("id").asText();

        String atendimento = lerJson(agendarAvulso(proximaSegunda().atTime(13, 0)).andExpect(status().isCreated()))
                .get("id").asText();
        mudarStatus(atendimento, "CONCLUIDO").andExpect(status().isOk());

        assertThat(saldo(vendido)).isEqualTo(5);
    }

    @Test
    void perfisNosPacotes() throws Exception {
        String modelo = criarModelo(3, 60);
        // Recepção não cria modelo; profissional não vende.
        mockMvc.perform(com(recepcao, post("/api/pacotes")).contentType(MediaType.APPLICATION_JSON)
                .content(modeloJson("Outro", 3, 60))).andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, post("/api/clientes/" + paciente.getId() + "/pacotes"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pacoteId\":\"" + modelo + "\"}"))
                .andExpect(status().isForbidden());
        // Toda a equipe vê os modelos.
        mockMvc.perform(com(tokenAna, get("/api/pacotes"))).andExpect(status().isOk());
        // A Ana só vê pacote de paciente que ela atende.
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + paciente.getId() + "/pacotes")))
                .andExpect(status().isNotFound());
        agendarAvulso(proximaSegunda().atTime(16, 0)).andExpect(status().isCreated());
        mockMvc.perform(com(tokenAna, get("/api/clientes/" + paciente.getId() + "/pacotes")))
                .andExpect(status().isOk());
    }

    @Test
    void outraClinicaNaoVendeNemVePacoteDestaClinica() throws Exception {
        String modelo = criarModelo(3, 60);
        Clinica outra = provisioning.criarClinica("Outra " + UUID.randomUUID().toString().substring(0, 8));
        String donoDaOutra = usuario(outra, Papel.OWNER, null);

        mockMvc.perform(com(donoDaOutra, get("/api/clientes/" + paciente.getId() + "/pacotes")))
                .andExpect(status().isNotFound());
        Cliente daOutra = new Cliente();
        daOutra.setClinica(outra);
        daOutra.setNome("Paciente da outra");
        daOutra.setTelefone("5511" + numero() + numero());
        clienteRepository.save(daOutra);
        mockMvc.perform(com(donoDaOutra, post("/api/clientes/" + daOutra.getId() + "/pacotes"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"pacoteId\":\"" + modelo + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------

    private String criarModelo(int sessoes, int validade) throws Exception {
        return lerJson(mockMvc.perform(com(dono, post("/api/pacotes")).contentType(MediaType.APPLICATION_JSON)
                .content(modeloJson("Pacote " + numero(), sessoes, validade))).andExpect(status().isCreated()))
                .get("id").asText();
    }

    private String modeloJson(String nome, int sessoes, int validade) {
        return """
                {"nome":"%s","servicoId":"%s","sessoes":%d,"validadeDias":%d,"preco":500.00}
                """.formatted(nome, servico.getId(), sessoes, validade);
    }

    private int saldo(String pacoteClienteId) throws Exception {
        return pacoteDoPaciente(pacoteClienteId).get("saldo").asInt();
    }

    private JsonNode pacoteDoPaciente(String pacoteClienteId) throws Exception {
        JsonNode lista = lerJson(mockMvc.perform(com(recepcao, get("/api/clientes/" + paciente.getId() + "/pacotes")))
                .andExpect(status().isOk()));
        for (JsonNode p : lista) {
            if (p.get("id").asText().equals(pacoteClienteId)) {
                return p;
            }
        }
        throw new AssertionError("pacote não listado");
    }

    private String serie(LocalDateTime inicio, String frequencia, int quantidade) {
        return """
                {"clienteId":"%s","profissionalId":"%s","servicos":[{"servicoId":"%s"}],
                 "inicio":"%s","frequencia":"%s","incluiSabado":false,"quantidade":%d}
                """.formatted(paciente.getId(), anaId, servico.getId(), inicio, frequencia, quantidade);
    }

    private ResultActions agendarAvulso(LocalDateTime horario) throws Exception {
        return mockMvc.perform(com(recepcao, post("/api/atendimentos")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"%s","dataAtendimento":"%s","profissionalId":"%s",
                         "servicos":[{"servicoId":"%s"}]}
                        """.formatted(paciente.getId(), horario, anaId, servico.getId())));
    }

    private ResultActions mudarStatus(String id, String novo) throws Exception {
        return mockMvc.perform(com(recepcao, patch("/api/atendimentos/" + id + "/status"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"novoStatus\":\"" + novo + "\"}"));
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
