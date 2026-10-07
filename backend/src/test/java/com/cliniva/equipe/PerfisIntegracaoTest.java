package com.cliniva.equipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.security.jwt.SupabaseJwtVerificador;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaProvisioningService;
import com.cliniva.tenancy.Papel;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalRepository;
import com.cliniva.tenancy.Usuario;
import com.cliniva.tenancy.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

/**
 * Perfis e isolamento ponta a ponta: rotas reais, filtro JWT real e banco
 * H2. Só a verificação da assinatura do token é simulada: cada token é o
 * próprio supabase_user_id do usuário de teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PerfisIntegracaoTest {

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
    private UUID biaId;
    private Servico servico;
    private Cliente pacienteDaAna;
    private Cliente pacienteDaBia;

    @BeforeEach
    void montarClinica() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        clinica = provisioning.criarClinica("Clínica Perfis " + sufixo);
        dono = usuario(clinica, Papel.OWNER, null);
        recepcao = usuario(clinica, Papel.RECEPCAO, null);

        // Profissionais criados pela API, para herdar o expediente do Geral.
        anaId = criarProfissional(dono, "Ana");
        biaId = criarProfissional(dono, "Bia");
        tokenAna = usuario(clinica, Papel.PROFISSIONAL, anaId);

        servico = new Servico();
        servico.setClinica(clinica);
        servico.setNome("Drenagem " + sufixo);
        servico.setValor(new BigDecimal("100.00"));
        servico.setDuracaoMinutos(60);
        servicoRepository.save(servico);

        pacienteDaAna = paciente(clinica, "Paciente A " + sufixo);
        pacienteDaBia = paciente(clinica, "Paciente B " + sufixo);
    }

    // ------------------------------------------------------------------
    // Permissões por perfil
    // ------------------------------------------------------------------

    @Test
    void recepcaoNaoConfiguraAClinica() throws Exception {
        mockMvc.perform(com(recepcao, get("/api/usuarios"))).andExpect(status().isForbidden());
        mockMvc.perform(com(recepcao, post("/api/profissionais")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Carla\"}")).andExpect(status().isForbidden());
        mockMvc.perform(com(recepcao, put("/api/agenda/horarios")).contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isForbidden());

        // ...mas usa a agenda, os pacientes e vê a equipe.
        mockMvc.perform(com(recepcao, get("/api/profissionais"))).andExpect(status().isOk());
        mockMvc.perform(com(recepcao, get("/api/clientes"))).andExpect(status().isOk());
        mockMvc.perform(com(recepcao, get("/api/resumo-do-dia"))).andExpect(status().isOk());
        mockMvc.perform(com(recepcao, post("/api/clientes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nova Paciente\",\"telefone\":\"11 98888-" + numero() + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void profissionalNaoCadastraPacienteNemMexeNoEstoque() throws Exception {
        mockMvc.perform(com(tokenAna, post("/api/clientes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nova\",\"telefone\":\"11 97777-" + numero() + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, post("/api/items")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Gel\",\"quantidadeEmEstoque\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, get("/api/resumo-do-dia"))).andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, get("/api/usuarios"))).andExpect(status().isForbidden());
        mockMvc.perform(com(tokenAna, get("/api/items"))).andExpect(status().isOk());
    }

    @Test
    void meDevolveOPerfilEOProfissional() throws Exception {
        mockMvc.perform(com(tokenAna, get("/api/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.profissionalId").value(anaId.toString()));
    }

    // ------------------------------------------------------------------
    // Agenda por profissional
    // ------------------------------------------------------------------

    @Test
    void profissionalNovoHerdaOExpedienteDaClinica() throws Exception {
        JsonNode horarios = lerJson(mockMvc.perform(com(dono, get("/api/agenda/horarios")
                .param("profissionalId", anaId.toString()))).andExpect(status().isOk()));
        assertThat(horarios).hasSize(6);
    }

    @Test
    void doisProfissionaisNoMesmoHorarioPassamOMesmoNao() throws Exception {
        LocalDateTime horario = proximaSegunda().atTime(10, 0);

        agendar(recepcao, pacienteDaAna, anaId, horario).andExpect(status().isCreated());
        agendar(recepcao, pacienteDaBia, biaId, horario).andExpect(status().isCreated());
        agendar(recepcao, pacienteDaBia, anaId, horario.plusMinutes(30))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Ana")));
    }

    @Test
    void profissionalSoVeAPropriaAgendaEOsPropriosPacientes() throws Exception {
        LocalDateTime horario = proximaSegunda().atTime(14, 0);
        agendar(recepcao, pacienteDaAna, anaId, horario).andExpect(status().isCreated());
        String idDaBia = lerJson(agendar(recepcao, pacienteDaBia, biaId, horario)
                .andExpect(status().isCreated())).get("id").asText();

        JsonNode agenda = lerJson(mockMvc.perform(com(tokenAna, get("/api/agenda")
                .param("data", horario.toLocalDate().toString())
                .param("profissionalId", biaId.toString()))).andExpect(status().isOk()));
        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).get("profissionalNome").asText()).isEqualTo("Ana");

        JsonNode pacientes = lerJson(mockMvc.perform(com(tokenAna, get("/api/clientes")))
                .andExpect(status().isOk()));
        assertThat(pacientes).extracting(p -> p.get("nome").asText()).containsExactly(pacienteDaAna.getNome());

        mockMvc.perform(com(tokenAna, get("/api/clientes/" + pacienteDaBia.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(tokenAna, get("/api/atendimentos/" + idDaBia)))
                .andExpect(status().isNotFound());
        agendar(tokenAna, pacienteDaAna, biaId, horario.plusHours(2)).andExpect(status().isForbidden());
    }

    @Test
    void agendamentoOnlineMostraQuemFazOServicoESemPreferenciaEscolheOLivre() throws Exception {
        LocalDateTime horario = proximaSegunda().atTime(16, 0);
        // Ana ocupada às 16h: "sem preferência" precisa cair na Bia.
        agendar(recepcao, pacienteDaAna, anaId, horario).andExpect(status().isCreated());

        JsonNode profissionais = lerJson(mockMvc.perform(get("/api/public/booking/" + clinica.getSlug()
                + "/profissionais").param("servicoId", servico.getId().toString())).andExpect(status().isOk()));
        assertThat(profissionais).extracting(p -> p.get("nome").asText()).contains("Ana", "Bia");

        mockMvc.perform(post("/api/public/booking/" + clinica.getSlug()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"servicoId":"%s","dataHora":"%s","nome":"Paciente Online","telefone":"11 96666-%s"}
                        """.formatted(servico.getId(), horario, numero())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profissional").value("Bia"));
    }

    // ------------------------------------------------------------------
    // Isolamento entre clínicas
    // ------------------------------------------------------------------

    @Test
    void outraClinicaNaoVeNemUsaProfissionalDestaClinica() throws Exception {
        Clinica outra = provisioning.criarClinica("Clínica Vizinha " + UUID.randomUUID().toString().substring(0, 8));
        String donoDaOutra = usuario(outra, Papel.OWNER, null);
        Cliente pacienteDaOutra = paciente(outra, "Paciente Vizinha " + numero());

        JsonNode equipe = lerJson(mockMvc.perform(com(donoDaOutra, get("/api/profissionais")))
                .andExpect(status().isOk()));
        assertThat(equipe).extracting(p -> p.get("id").asText()).doesNotContain(anaId.toString());

        mockMvc.perform(com(donoDaOutra, put("/api/profissionais/" + anaId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Invasora\"}")).andExpect(status().isNotFound());
        agendar(donoDaOutra, pacienteDaOutra, anaId, proximaSegunda().atTime(9, 0))
                .andExpect(status().isNotFound());
        mockMvc.perform(com(donoDaOutra, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"Intrusa","email":"intrusa-%s@exemplo.test","papel":"PROFISSIONAL","profissionalId":"%s"}
                        """.formatted(numero(), anaId))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Usuários e acessos
    // ------------------------------------------------------------------

    @Test
    void donoCriaAcessoDeProfissionalLigadoAoCadastro() throws Exception {
        String email = "bia-" + numero() + "@exemplo.test";
        mockMvc.perform(com(dono, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"Bia","email":"%s","papel":"PROFISSIONAL","profissionalId":"%s"}
                        """.formatted(email, biaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.papel").value("PROFISSIONAL"))
                .andExpect(jsonPath("$.usuario.profissionalNome").value("Bia"));

        // A Ana já tem acesso: um segundo login para ela é recusado.
        mockMvc.perform(com(dono, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"Outra","email":"outra-%s@exemplo.test","papel":"PROFISSIONAL","profissionalId":"%s"}
                        """.formatted(numero(), anaId)))
                .andExpect(status().isConflict());

        // PROFISSIONAL sem profissional ligado não existe.
        mockMvc.perform(com(dono, post("/api/usuarios")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nome":"Solta","email":"solta-%s@exemplo.test","papel":"PROFISSIONAL"}
                        """.formatted(numero())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

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
            Profissional profissional = profissionalRepository.findById(profissionalId).orElseThrow();
            usuario.setProfissional(profissional);
        }
        usuarioRepository.save(usuario);

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(token);
        when(verificador.verificar(token)).thenReturn(claims);
        return token;
    }

    private UUID criarProfissional(String token, String nome) throws Exception {
        JsonNode criado = lerJson(mockMvc.perform(com(token, post("/api/profissionais"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\"}"))
                .andExpect(status().isCreated()));
        return UUID.fromString(criado.get("id").asText());
    }

    private Cliente paciente(Clinica clinicaDoPaciente, String nome) {
        Cliente cliente = new Cliente();
        cliente.setClinica(clinicaDoPaciente);
        cliente.setNome(nome);
        cliente.setTelefone("5511" + numero() + numero());
        return clienteRepository.save(cliente);
    }

    private org.springframework.test.web.servlet.ResultActions agendar(String token, Cliente paciente,
            UUID profissionalId, LocalDateTime horario) throws Exception {
        return mockMvc.perform(com(token, post("/api/atendimentos")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clienteId":"%s","dataAtendimento":"%s","profissionalId":"%s",
                         "servicos":[{"servicoId":"%s"}]}
                        """.formatted(paciente.getId(), horario, profissionalId, servico.getId())));
    }

    private MockHttpServletRequestBuilder com(String token, MockHttpServletRequestBuilder requisicao) {
        return requisicao.header("Authorization", "Bearer " + token);
    }

    private JsonNode lerJson(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    /** Segunda-feira futura dentro da janela de 30 dias (a clínica abre de segunda a sábado). */
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
