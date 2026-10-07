package com.cliniva.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cliniva.agenda.AgendaService;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO.ItemUsadoDTO;
import com.cliniva.atendimento.dtos.SerieDtos.OcorrenciaDTO;
import com.cliniva.atendimento.dtos.SerieDtos.SerieRequestDTO;
import com.cliniva.atendimento.enums.FrequenciaSerie;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.SerieAgendamento;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.SerieAgendamentoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.HorarioIndisponivelException;
import com.cliniva.servico.Servico;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Profissional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SerieServiceTest {

    /** Quarta-feira, 07/10/2026. */
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 7);
    private static final Clock CLOCK = Clock.fixed(HOJE.atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo"))
            .toInstant(), ZoneId.of("America/Sao_Paulo"));
    private static final Clinica CLINICA = new Clinica();
    private static final Profissional ANA = new Profissional();
    private static final Cliente PACIENTE = new Cliente();
    private static final UUID SERVICO_ID = UUID.randomUUID();

    @Mock
    private AtendimentoService atendimentoService;
    @Mock
    private AgendaService agendaService;
    @Mock
    private AtendimentoRepository atendimentoRepository;
    @Mock
    private SerieAgendamentoRepository serieRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ClinicaContext clinicaContext;

    private SerieService serieService;

    @BeforeEach
    void setUp() {
        CLINICA.setId(UUID.randomUUID());
        ANA.setId(UUID.randomUUID());
        ANA.setNome("Ana");
        PACIENTE.setId(UUID.randomUUID());
        serieService = new SerieService(atendimentoService, agendaService, atendimentoRepository, serieRepository,
                clienteRepository, clinicaContext, CLOCK);

        Servico servico = new Servico();
        servico.setId(SERVICO_ID);
        servico.setDuracaoMinutos(50);
        when(atendimentoService.resolverServicos(eq(CLINICA), any())).thenReturn(List.of(servico));
        when(atendimentoService.duracaoTotal(any())).thenReturn(50);
        when(atendimentoService.profissionalPermitido(CLINICA, ANA.getId())).thenReturn(ANA);
        when(clienteRepository.findByIdAndClinica(PACIENTE.getId(), CLINICA)).thenReturn(Optional.of(PACIENTE));
        when(agendaService.motivoIndisponivel(eq(CLINICA), eq(ANA), any(), eq(50), isNull(), anyInt()))
                .thenReturn(Optional.empty());
        when(clinicaContext.profissionalRestrito()).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // Geração das datas
    // ------------------------------------------------------------------

    @Test
    void cadaFrequenciaAndaOPassoCerto() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 12, 9, 0);
        assertThat(SerieService.candidatas(inicio, FrequenciaSerie.DIARIA, 3))
                .containsExactly(inicio, inicio.plusDays(1), inicio.plusDays(2));
        assertThat(SerieService.candidatas(inicio, FrequenciaSerie.SEMANAL, 3))
                .containsExactly(inicio, inicio.plusDays(7), inicio.plusDays(14));
        assertThat(SerieService.candidatas(inicio, FrequenciaSerie.QUINZENAL, 3))
                .containsExactly(inicio, inicio.plusDays(14), inicio.plusDays(28));
        assertThat(SerieService.candidatas(inicio, FrequenciaSerie.MENSAL, 3))
                .containsExactly(inicio, LocalDateTime.of(2026, 11, 12, 9, 0), LocalDateTime.of(2026, 12, 12, 9, 0));
    }

    @Test
    void mensalNoDia31UsaOUltimoDiaDoMesCurtoESemPerderODia31() {
        LocalDateTime inicio = LocalDateTime.of(2027, 1, 31, 10, 0);
        assertThat(SerieService.candidatas(inicio, FrequenciaSerie.MENSAL, 4)).extracting(LocalDateTime::toLocalDate)
                .containsExactly(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31),
                        LocalDate.of(2027, 4, 30));
    }

    @Test
    void domingoSempreFicaForaESabadoSoQuandoIncluido() {
        LocalDate sabado = LocalDate.of(2026, 10, 10);
        LocalDate domingo = LocalDate.of(2026, 10, 11);
        assertThat(SerieService.puloDeFimDeSemana(domingo, true)).contains("Domingo");
        assertThat(SerieService.puloDeFimDeSemana(sabado, false)).isPresent();
        assertThat(SerieService.puloDeFimDeSemana(sabado, true)).isEmpty();
        assertThat(SerieService.puloDeFimDeSemana(sabado.plusDays(2), false)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Prévia
    // ------------------------------------------------------------------

    @Test
    void diariaPulaOFimDeSemanaSemListarECompletaAQuantidade() {
        // Sexta 09/10 → sex, seg, ter (sábado e domingo não entram).
        var previa = serieService.previa(CLINICA,
                pedido(LocalDateTime.of(2026, 10, 9, 9, 0), FrequenciaSerie.DIARIA, false, null, 3));

        assertThat(previa.ocorrencias()).extracting(o -> o.dataHora().toLocalDate())
                .containsExactly(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13));
        assertThat(previa.disponiveis()).isEqualTo(3);
    }

    @Test
    void diariaComSabadoInclui() {
        var previa = serieService.previa(CLINICA,
                pedido(LocalDateTime.of(2026, 10, 9, 9, 0), FrequenciaSerie.DIARIA, true, null, 3));

        assertThat(previa.ocorrencias()).extracting(o -> o.dataHora().toLocalDate())
                .containsExactly(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12));
    }

    @Test
    void semanalNoDomingoListaODiaComoPulado() {
        var previa = serieService.previa(CLINICA,
                pedido(LocalDateTime.of(2026, 10, 11, 9, 0), FrequenciaSerie.SEMANAL, false,
                        LocalDate.of(2026, 10, 25), null));

        assertThat(previa.ocorrencias()).hasSize(3).allSatisfy(o -> {
            assertThat(o.disponivel()).isFalse();
            assertThat(o.motivo()).isEqualTo("Domingo");
        });
        assertThat(previa.disponiveis()).isZero();
    }

    @Test
    void diaOcupadoEListadoComOMotivoEAQuantidadeContinuaDepoisDele() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 12, 10, 0);
        when(agendaService.motivoIndisponivel(eq(CLINICA), eq(ANA), eq(inicio.plusWeeks(1)), eq(50), isNull(),
                anyInt())).thenReturn(Optional.of("Horário já ocupado na agenda de Ana"));

        var previa = serieService.previa(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false, null, 3));

        assertThat(previa.ocorrencias()).extracting(OcorrenciaDTO::disponivel)
                .containsExactly(true, false, true, true);
        assertThat(previa.ocorrencias().get(1).motivo()).contains("ocupado");
        assertThat(previa.disponiveis()).isEqualTo(3);
        assertThat(previa.puladas()).isEqualTo(1);
    }

    @Test
    void fimPorDataParaNaData() {
        var previa = serieService.previa(CLINICA, pedido(LocalDateTime.of(2026, 10, 12, 10, 0),
                FrequenciaSerie.QUINZENAL, false, LocalDate.of(2026, 11, 23), null));

        assertThat(previa.ocorrencias()).extracting(o -> o.dataHora().toLocalDate())
                .containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 9),
                        LocalDate.of(2026, 11, 23));
    }

    @Test
    void naoPassaDaJanelaDaEquipe() {
        var previa = serieService.previa(CLINICA,
                pedido(LocalDateTime.of(2026, 10, 12, 10, 0), FrequenciaSerie.MENSAL, false, null, 100));

        LocalDate limite = HOJE.plusDays(AgendaService.JANELA_INTERNA_DIAS);
        assertThat(previa.ocorrencias()).allSatisfy(o -> assertThat(o.dataHora().toLocalDate()).isBeforeOrEqualTo(limite));
        assertThat(previa.disponiveis()).isLessThan(100);
    }

    @Test
    void exigeDataFinalOuQuantidadeNuncaOsDois() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 12, 10, 0);
        assertThatThrownBy(() -> serieService.previa(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false, null,
                null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serieService.previa(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false,
                LocalDate.of(2026, 12, 1), 4))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serieService.previa(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false, null,
                0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serieService.previa(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false,
                LocalDate.of(2026, 10, 1), null))).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Criação e cancelamento
    // ------------------------------------------------------------------

    @Test
    void criaSoAsDatasLivresLigadasASerie() {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 12, 10, 0);
        when(agendaService.motivoIndisponivel(eq(CLINICA), eq(ANA), eq(inicio.plusWeeks(1)), eq(50), isNull(),
                anyInt())).thenReturn(Optional.of("Ana não atende neste dia"));

        var criada = serieService.criar(CLINICA, pedido(inicio, FrequenciaSerie.SEMANAL, false, null, 2));

        ArgumentCaptor<CreateAtendimentoRequestDTO> sessoes = ArgumentCaptor.forClass(CreateAtendimentoRequestDTO.class);
        ArgumentCaptor<SerieAgendamento> serie = ArgumentCaptor.forClass(SerieAgendamento.class);
        verify(atendimentoService, times(2)).createAtendimento(eq(CLINICA), sessoes.capture(), serie.capture());
        assertThat(sessoes.getAllValues()).extracting(CreateAtendimentoRequestDTO::dataAtendimento)
                .containsExactly(inicio, inicio.plusWeeks(2));
        assertThat(serie.getValue().getQuantidade()).isEqualTo(2);
        assertThat(serie.getValue().getFrequencia()).isEqualTo(FrequenciaSerie.SEMANAL);
        assertThat(criada.puladas()).hasSize(1);
        verify(serieRepository).save(any());
    }

    @Test
    void semNenhumaDataLivreNaoCriaNada() {
        when(agendaService.motivoIndisponivel(any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(Optional.of("Ana não atende neste dia"));

        assertThatThrownBy(() -> serieService.criar(CLINICA, pedido(LocalDateTime.of(2026, 10, 12, 10, 0),
                FrequenciaSerie.SEMANAL, false, LocalDate.of(2026, 10, 26), null)))
                .isInstanceOf(HorarioIndisponivelException.class);
        verify(serieRepository, never()).save(any());
    }

    @Test
    void itensExtrasNaoEntramEmSerie() {
        var comItem = new SerieRequestDTO(PACIENTE.getId(), ANA.getId(),
                List.of(new ServicoSelecionadoDTO(SERVICO_ID,
                        List.of(new ItemUsadoDTO(UUID.randomUUID(), java.math.BigDecimal.ONE)))),
                LocalDateTime.of(2026, 10, 12, 10, 0), FrequenciaSerie.SEMANAL, false, null, 2);

        assertThatThrownBy(() -> serieService.criar(CLINICA, comItem)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelarSeguintesCancelaSoAsAgendadasDaquiParaFrente() {
        SerieAgendamento serie = new SerieAgendamento();
        serie.setId(UUID.randomUUID());
        Atendimento esta = sessao(serie, StatusAtendimento.AGENDADO, ANA);
        Atendimento concluida = sessao(serie, StatusAtendimento.CONCLUIDO, ANA);
        Atendimento proxima = sessao(serie, StatusAtendimento.AGENDADO, ANA);
        when(atendimentoService.buscarVisivel(CLINICA, esta.getId())).thenReturn(esta);
        when(atendimentoRepository.findBySerie_IdAndDataAtendimentoGreaterThanEqualOrderByDataAtendimentoAsc(
                serie.getId(), esta.getDataAtendimento())).thenReturn(List.of(esta, concluida, proxima));

        var resultado = serieService.cancelarSeguintes(CLINICA, esta.getId());

        assertThat(resultado.cancelados()).isEqualTo(2);
        verify(atendimentoService).alterarStatus(CLINICA, esta.getId(), StatusAtendimento.CANCELADO);
        verify(atendimentoService).alterarStatus(CLINICA, proxima.getId(), StatusAtendimento.CANCELADO);
        verify(atendimentoService, never()).alterarStatus(CLINICA, concluida.getId(), StatusAtendimento.CANCELADO);
    }

    @Test
    void profissionalNaoCancelaSessaoQueFoiParaOutraAgenda() {
        Profissional bia = new Profissional();
        bia.setId(UUID.randomUUID());
        SerieAgendamento serie = new SerieAgendamento();
        serie.setId(UUID.randomUUID());
        Atendimento esta = sessao(serie, StatusAtendimento.AGENDADO, ANA);
        Atendimento daBia = sessao(serie, StatusAtendimento.AGENDADO, bia);
        when(clinicaContext.profissionalRestrito()).thenReturn(Optional.of(ANA.getId()));
        when(atendimentoService.buscarVisivel(CLINICA, esta.getId())).thenReturn(esta);
        when(atendimentoRepository.findBySerie_IdAndDataAtendimentoGreaterThanEqualOrderByDataAtendimentoAsc(
                any(), any())).thenReturn(List.of(esta, daBia));

        assertThat(serieService.cancelarSeguintes(CLINICA, esta.getId()).cancelados()).isEqualTo(1);
        verify(atendimentoService, never()).alterarStatus(CLINICA, daBia.getId(), StatusAtendimento.CANCELADO);
    }

    @Test
    void atendimentoAvulsoNaoTemSeguintes() {
        Atendimento avulso = sessao(null, StatusAtendimento.AGENDADO, ANA);
        when(atendimentoService.buscarVisivel(CLINICA, avulso.getId())).thenReturn(avulso);

        assertThatThrownBy(() -> serieService.cancelarSeguintes(CLINICA, avulso.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Atendimento sessao(SerieAgendamento serie, StatusAtendimento status, Profissional profissional) {
        Atendimento a = new Atendimento();
        a.setId(UUID.randomUUID());
        a.setSerie(serie);
        a.setStatus(status);
        a.setProfissional(profissional);
        a.setDataAtendimento(LocalDateTime.of(2026, 10, 12, 10, 0));
        return a;
    }

    private static SerieRequestDTO pedido(LocalDateTime inicio, FrequenciaSerie frequencia, boolean sabado,
            LocalDate dataFim, Integer quantidade) {
        return new SerieRequestDTO(PACIENTE.getId(), ANA.getId(), List.of(new ServicoSelecionadoDTO(SERVICO_ID, null)),
                inicio, frequencia, sabado, dataFim, quantidade);
    }
}
