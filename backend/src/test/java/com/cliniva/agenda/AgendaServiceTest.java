package com.cliniva.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.agenda.dtos.HorarioRequestDTO;
import com.cliniva.agenda.model.HorarioAtendimento;
import com.cliniva.agenda.model.HorarioAtendimentoId;
import com.cliniva.agenda.repository.HorarioAtendimentoRepository;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.exception.HorarioIndisponivelException;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.ClinicaRepository;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    /** Relógio fixo: 2026-09-25 (sexta) 09:00 BRT. */
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-25T12:00:00Z"), ZONA);

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 25);

    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);

    private static final Clinica CLINICA = clinica();

    @Mock
    private AtendimentoRepository atendimentoRepository;
    @Mock
    private AtendimentoServicoRepository atendimentoServicoRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private HorarioAtendimentoRepository horarioRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private ProfissionalService profissionalService;
    @Mock
    private ClinicaContext clinicaContext;

    /** Profissional fallback: desde a migration 08 o expediente é por pessoa. */
    private static final Profissional GERAL = profissional("Geral");

    @InjectMocks
    private AgendaService agendaService;

    @BeforeEach
    void setUpProfissionalGeral() {
        lenient().when(profissionalService.garantirGeral(CLINICA)).thenReturn(GERAL);
        lenient().when(profissionalService.aptos(eq(CLINICA), any())).thenReturn(List.of(GERAL));
    }

    @BeforeEach
    void setUpClock() {
        ReflectionTestUtils.setField(agendaService, "clock", CLOCK);
        when(clinicaRepository.findByIdParaUpdate(CLINICA.getId())).thenReturn(Optional.of(CLINICA));
    }

    private static Clinica clinica() {
        Clinica clinica = new Clinica();
        ReflectionTestUtils.setField(clinica, "id", UUID.randomUUID());
        clinica.setNome("Clínica A");
        clinica.setSlug("clinica-a");
        return clinica;
    }

    private Servico servico(String nome, String valor, int duracao) {
        Servico servico = new Servico();
        servico.setClinica(CLINICA);
        servico.setNome(nome);
        servico.setValor(new BigDecimal(valor));
        servico.setDuracaoMinutos(duracao);
        return servico;
    }

    private static Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setClinica(CLINICA);
        p.setNome(nome);
        p.setAtivo(true);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        return p;
    }

    private HorarioAtendimento horario(int dia, String abertura, String fechamento, boolean ativo) {
        return horario(GERAL, dia, abertura, fechamento, ativo);
    }

    private HorarioAtendimento horario(Profissional profissional, int dia, String abertura, String fechamento,
            boolean ativo) {
        HorarioAtendimento h = new HorarioAtendimento();
        h.setId(new HorarioAtendimentoId(CLINICA.getId(), profissional.getId(), dia));
        h.setClinica(CLINICA);
        h.setProfissional(profissional);
        h.setAbertura(LocalTime.parse(abertura));
        h.setFechamento(LocalTime.parse(fechamento));
        h.setAtivo(ativo);
        return h;
    }

    private Atendimento atendimento(UUID id, LocalDateTime inicio, int duracao, StatusAtendimento status) {
        return atendimento(GERAL, id, inicio, duracao, status);
    }

    private Atendimento atendimento(Profissional profissional, UUID id, LocalDateTime inicio, int duracao,
            StatusAtendimento status) {
        Atendimento a = new Atendimento();
        ReflectionTestUtils.setField(a, "id", id);
        a.setClinica(CLINICA);
        a.setProfissional(profissional);
        a.setDataAtendimento(inicio);
        a.setDuracaoMinutos(duracao);
        a.setStatus(status);

        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", UUID.randomUUID());
        cliente.setNome("Maria");
        cliente.setTelefone("11999999999");
        a.setCliente(cliente);
        return a;
    }

    private AtendimentoServico atendimentoServico(Atendimento a, Servico s) {
        AtendimentoServico as = new AtendimentoServico();
        as.setAtendimento(a);
        as.setServico(s);
        as.setValorCobrado(s.getValor());
        return as;
    }

    @Test
    void deveListarDiaOrdenadoPorHoraComDuracaoEValorTotal() {
        Servico limpeza = servico("Limpeza de Pele", "120.00", 45);
        Servico massagem = servico("Massagem", "90.00", 60);

        Atendimento tarde = atendimento(UUID.randomUUID(), SEGUNDA.atTime(14, 0), 60, StatusAtendimento.AGENDADO);
        Atendimento manha = atendimento(UUID.randomUUID(), SEGUNDA.atTime(9, 0), 45, StatusAtendimento.AGENDADO);

        when(atendimentoRepository.findPorIntervalo(any(), any(), any()))
                .thenReturn(List.of(tarde, manha));
        when(atendimentoServicoRepository.findByAtendimento(manha))
                .thenReturn(List.of(atendimentoServico(manha, limpeza)));
        when(atendimentoServicoRepository.findByAtendimento(tarde))
                .thenReturn(List.of(atendimentoServico(tarde, massagem)));

        var itens = agendaService.listarDia(CLINICA, SEGUNDA, null);

        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).clienteNome()).isEqualTo("Maria");
        assertThat(itens.get(0).inicio()).isEqualTo(SEGUNDA.atTime(9, 0));
        assertThat(itens.get(0).fim()).isEqualTo(SEGUNDA.atTime(9, 45));
        assertThat(itens.get(0).duracaoMinutos()).isEqualTo(45);
        assertThat(itens.get(0).valorTotal()).isEqualByComparingTo("120.00");
        assertThat(itens.get(1).inicio()).isEqualTo(SEGUNDA.atTime(14, 0));
    }

    @Test
    void deveGerarHorariosLivresDentroDoExpediente() {
        Servico servico = servico("Limpeza", "100.00", 30);
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "10:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of());

        var disponibilidade = agendaService.disponibilidadeDia(CLINICA, SEGUNDA, UUID.randomUUID(), null);

        assertThat(disponibilidade.horarios()).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    @Test
    void deveIgnorarHorarioJaOcupado() {
        Servico servico = servico("Limpeza", "100.00", 30);
        Atendimento ocupado = atendimento(UUID.randomUUID(), SEGUNDA.atTime(8, 30), 30,
                StatusAtendimento.AGENDADO);

        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "10:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any()))
                .thenReturn(List.of(ocupado));

        var disponibilidade = agendaService.disponibilidadeDia(CLINICA, SEGUNDA, UUID.randomUUID(), null);

        assertThat(disponibilidade.horarios()).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    @Test
    void deveRetornarListaVaziaQuandoClinicaFechadaNoDia() {
        Servico servico = servico("Limpeza", "100.00", 30);
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId())).thenReturn(List.of());

        var disponibilidade = agendaService.disponibilidadeDia(CLINICA, SEGUNDA, UUID.randomUUID(), null);

        assertThat(disponibilidade.horarios()).isEmpty();
    }

    @Test
    void disponibilidadeNaoDeveOferecerHorariosNoPassadoNemAlemDaJanela() {
        Servico servico = servico("Limpeza", "100.00", 30);
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));

        var passado = agendaService.disponibilidadeDia(CLINICA, HOJE.minusDays(3), UUID.randomUUID(), null);
        var longe = agendaService.disponibilidadeDia(CLINICA, HOJE.plusDays(31), UUID.randomUUID(), null);

        assertThat(passado.horarios()).isEmpty();
        assertThat(longe.horarios()).isEmpty();
    }

    @Test
    void naoDeveValidarHorarioQueConflitaComOutroAtendimento() {
        Atendimento ocupado = atendimento(UUID.randomUUID(), SEGUNDA.atTime(9, 0), 30,
                StatusAtendimento.AGENDADO);
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any()))
                .thenReturn(List.of(ocupado));

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(9, 0), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("ocupado");
    }

    @Test
    void naoDeveValidarHorarioForaDoExpediente() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(19, 0), 45, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Fora do horário");
    }

    @Test
    void naoDeveValidarQuandoClinicaNaoAbreNoDia() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId())).thenReturn(List.of());
        LocalDate domingo = SEGUNDA.plusDays(6);

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, domingo.atTime(10, 0), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("não atende");
    }

    @Test
    void naoDeveAceitarDataPassadaNemAlemDaJanelaDeTrintaDias() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));
        LocalDate outraSegunda = SEGUNDA.minusWeeks(1);

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, outraSegunda.atTime(10, 0), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("passada");

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, HOJE.plusDays(31).atTime(10, 0), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("próximos");
    }

    @Test
    void naoDeveAceitarAtendimentoQueAtravessaAMeiaNoite() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "22:00", "23:00", true)));

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(22, 30), 60, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Fora do horário");
    }

    @Test
    void naoDeveAceitarDuracaoZeroOuAcimaDoTeto() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(10, 0), 0, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Duração");

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(10, 0), 5000, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Duração");
    }

    @Test
    void deveAdquirirLockPessimistaDaClinicaAoValidar() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of());

        agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(10, 0), 45, null);

        verify(clinicaRepository).findByIdParaUpdate(CLINICA.getId());
    }

    @Test
    void devePermitirHorarioLivre() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of());

        agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(10, 0), 45, null);
    }

    @Test
    void deveIgnorarOProprioAtendimentoNaRemarcacao() {
        UUID proprio = UUID.randomUUID();
        Atendimento atual = atendimento(proprio, SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true)));
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(atual));

        agendaService.validarDisponibilidade(CLINICA, GERAL, SEGUNDA.atTime(9, 0), 60, proprio);
    }

    @Test
    void deveAtualizarHorariosComUpsert() {
        HorarioAtendimentoId idExistente = new HorarioAtendimentoId(CLINICA.getId(), GERAL.getId(), 1);
        HorarioAtendimento existente = horario(1, "08:00", "18:00", true);
        when(horarioRepository.findById(idExistente)).thenReturn(Optional.of(existente));
        when(horarioRepository.findById(new HorarioAtendimentoId(CLINICA.getId(), GERAL.getId(), 2)))
                .thenReturn(Optional.empty());
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(existente));

        agendaService.atualizarHorarios(CLINICA, null, List.of(
                new HorarioRequestDTO(1, LocalTime.of(9, 0), LocalTime.of(17, 0), true),
                new HorarioRequestDTO(2, LocalTime.of(8, 0), LocalTime.of(12, 0), true)));

        assertThat(existente.getAbertura()).isEqualTo(LocalTime.of(9, 0));
        verify(horarioRepository, times(2)).save(any(HorarioAtendimento.class));
    }

    @Test
    void deveRejeitarDiaDuplicadoNaListaDeExpediente() {
        assertThatThrownBy(() -> agendaService.atualizarHorarios(CLINICA, null, List.of(
                new HorarioRequestDTO(1, LocalTime.of(9, 0), LocalTime.of(17, 0), true),
                new HorarioRequestDTO(1, LocalTime.of(8, 0), LocalTime.of(12, 0), true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void deveSemearPadraoDeSegundaASabado() {
        when(horarioRepository.existsById(any())).thenReturn(false);

        agendaService.semearPadrao(CLINICA);

        verify(horarioRepository, times(6)).save(any(HorarioAtendimento.class));
    }

    @Test
    void semearPadraoNaoDeveDuplicarExpedienteExistente() {
        when(horarioRepository.existsById(any())).thenReturn(true);

        agendaService.semearPadrao(CLINICA);

        verify(horarioRepository, times(0)).save(any(HorarioAtendimento.class));
    }

    // ---------------------------------------------------------------------
    // Agenda por profissional (Fase 2)
    // ---------------------------------------------------------------------

    private static final Profissional ANA = profissional("Ana");
    private static final Profissional BIA = profissional("Bia");

    private void expedienteDeSegunda(Profissional profissional, String abertura, String fechamento) {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, profissional.getId()))
                .thenReturn(List.of(horario(profissional, 1, abertura, fechamento, true)));
    }

    @Test
    void doisProfissionaisPodemAtenderNoMesmoHorario() {
        expedienteDeSegunda(ANA, "08:00", "18:00");
        expedienteDeSegunda(BIA, "08:00", "18:00");
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna));

        agendaService.validarDisponibilidade(CLINICA, BIA, SEGUNDA.atTime(9, 0), 60, null);

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, ANA, SEGUNDA.atTime(9, 30), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Ana");
    }

    @Test
    void canceladoNaoBloqueiaAgendaDoProfissional() {
        expedienteDeSegunda(ANA, "08:00", "18:00");
        Atendimento cancelado = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60,
                StatusAtendimento.CANCELADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(cancelado));

        agendaService.validarDisponibilidade(CLINICA, ANA, SEGUNDA.atTime(9, 0), 60, null);
    }

    @Test
    void profissionalSemExpedienteNoDiaNaoPodeSerAgendado() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, BIA.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> agendaService.validarDisponibilidade(CLINICA, BIA, SEGUNDA.atTime(9, 0), 30, null))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("Bia não atende");
    }

    @Test
    void disponibilidadeDeUmProfissionalIgnoraAgendaDosOutros() {
        Servico servico = servico("Drenagem", "100.00", 60);
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(profissionalService.buscar(CLINICA, BIA.getId())).thenReturn(BIA);
        expedienteDeSegunda(BIA, "08:00", "10:00");
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(8, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna));

        var disponibilidade = agendaService.disponibilidadeDia(CLINICA, SEGUNDA, UUID.randomUUID(), BIA.getId());

        assertThat(disponibilidade.horarios()).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0));
    }

    @Test
    void semPreferenciaUneOsHorariosDeQuemFazOServico() {
        Servico servico = servico("Drenagem", "100.00", 60);
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(profissionalService.aptos(eq(CLINICA), any())).thenReturn(List.of(ANA, BIA));
        expedienteDeSegunda(ANA, "08:00", "09:00");
        expedienteDeSegunda(BIA, "10:00", "11:00");
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of());

        var disponibilidade = agendaService.disponibilidadeDia(CLINICA, SEGUNDA, UUID.randomUUID(), null);

        assertThat(disponibilidade.horarios()).containsExactly(LocalTime.of(8, 0), LocalTime.of(10, 0));
    }

    @Test
    void semPreferenciaEscolheQuemEstaLivre() {
        Servico servico = servico("Drenagem", "100.00", 60);
        when(profissionalService.aptos(eq(CLINICA), any())).thenReturn(List.of(ANA, BIA));
        expedienteDeSegunda(ANA, "08:00", "18:00");
        expedienteDeSegunda(BIA, "08:00", "18:00");
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna));

        Profissional escolhido = agendaService.escolherLivre(CLINICA, List.of(servico), SEGUNDA.atTime(9, 0), 60);

        assertThat(escolhido).isSameAs(BIA);
    }

    @Test
    void semPreferenciaSemNinguemLivreRecusa() {
        Servico servico = servico("Drenagem", "100.00", 60);
        when(profissionalService.aptos(eq(CLINICA), any())).thenReturn(List.of(ANA));
        expedienteDeSegunda(ANA, "08:00", "18:00");
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna));

        assertThatThrownBy(() -> agendaService.escolherLivre(CLINICA, List.of(servico), SEGUNDA.atTime(9, 0), 60))
                .isInstanceOf(HorarioIndisponivelException.class)
                .hasMessageContaining("ocupado");
    }

    @Test
    void profissionalLogadoSoVeAPropriaAgenda() {
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        Atendimento daBia = atendimento(BIA, UUID.randomUUID(), SEGUNDA.atTime(10, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna, daBia));
        when(clinicaContext.profissionalRestrito()).thenReturn(Optional.of(ANA.getId()));

        // Mesmo pedindo a agenda da Bia, a Ana só recebe a dela.
        var itens = agendaService.listarDia(CLINICA, SEGUNDA, BIA.getId());

        assertThat(itens).extracting(item -> item.profissionalNome()).containsExactly("Ana");
    }

    @Test
    void filtroDeProfissionalNaAgendaDaRecepcao() {
        Atendimento daAna = atendimento(ANA, UUID.randomUUID(), SEGUNDA.atTime(9, 0), 60, StatusAtendimento.AGENDADO);
        Atendimento daBia = atendimento(BIA, UUID.randomUUID(), SEGUNDA.atTime(10, 0), 60, StatusAtendimento.AGENDADO);
        when(atendimentoRepository.findPorIntervalo(any(), any(), any())).thenReturn(List.of(daAna, daBia));

        assertThat(agendaService.listarDia(CLINICA, SEGUNDA, BIA.getId()))
                .extracting(item -> item.profissionalNome()).containsExactly("Bia");
        assertThat(agendaService.listarDia(CLINICA, SEGUNDA, null)).hasSize(2);
    }

    @Test
    void profissionalNovoRecebeOExpedienteDoGeral() {
        when(horarioRepository.findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(CLINICA, GERAL.getId()))
                .thenReturn(List.of(horario(1, "08:00", "18:00", true), horario(2, "09:00", "12:00", false)));
        when(horarioRepository.existsById(any())).thenReturn(false);
        when(horarioRepository.findById(any())).thenReturn(Optional.empty());

        agendaService.copiarExpedienteGeral(CLINICA, ANA);

        org.mockito.ArgumentCaptor<HorarioAtendimento> salvo = org.mockito.ArgumentCaptor.forClass(HorarioAtendimento.class);
        verify(horarioRepository, times(2)).save(salvo.capture());
        assertThat(salvo.getAllValues()).allMatch(h -> h.getProfissional() == ANA);
        assertThat(salvo.getAllValues().get(1).isAtivo()).isFalse();
    }
}
