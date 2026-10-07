package com.cliniva.atendimento;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.AgendaService;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO;
import com.cliniva.atendimento.dtos.SerieDtos.CancelamentoSerieDTO;
import com.cliniva.atendimento.dtos.SerieDtos.OcorrenciaDTO;
import com.cliniva.atendimento.dtos.SerieDtos.PreviaSerieDTO;
import com.cliniva.atendimento.dtos.SerieDtos.SerieCriadaDTO;
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
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Profissional;

import lombok.RequiredArgsConstructor;

/**
 * Agendamento recorrente. A prévia lista cada data com o motivo quando não
 * dá para marcar (domingo, sábado fora da série, dia sem expediente, horário
 * ocupado); a criação marca só as datas livres e devolve as puladas.
 */
@Service
@RequiredArgsConstructor
public class SerieService {

    /** Teto de sessões de uma série. */
    public static final int MAXIMO_SESSOES = 100;

    /** Teto de datas examinadas, para uma série que nunca acha horário livre. */
    private static final int MAXIMO_CANDIDATAS = 400;

    private final AtendimentoService atendimentoService;
    private final AgendaService agendaService;
    private final AtendimentoRepository atendimentoRepository;
    private final SerieAgendamentoRepository serieRepository;
    private final ClienteRepository clienteRepository;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PreviaSerieDTO previa(Clinica clinica, SerieRequestDTO request) {
        List<OcorrenciaDTO> ocorrencias = ocorrencias(clinica, request);
        int disponiveis = (int) ocorrencias.stream().filter(OcorrenciaDTO::disponivel).count();
        return new PreviaSerieDTO(ocorrencias, disponiveis, ocorrencias.size() - disponiveis);
    }

    @Transactional
    public SerieCriadaDTO criar(Clinica clinica, SerieRequestDTO request) {
        boolean temItensExtras = request.servicos().stream()
                .anyMatch(s -> s.itensExtras() != null && !s.itensExtras().isEmpty());
        if (temItensExtras) {
            // O estoque sairia de uma vez para todas as sessões futuras.
            throw new IllegalArgumentException("Itens extras não podem ser lançados em agendamento recorrente");
        }

        List<OcorrenciaDTO> ocorrencias = ocorrencias(clinica, request);
        List<OcorrenciaDTO> livres = ocorrencias.stream().filter(OcorrenciaDTO::disponivel).toList();
        if (livres.isEmpty()) {
            throw new HorarioIndisponivelException("Nenhuma data da série está disponível");
        }

        Cliente cliente = clienteRepository.findByIdAndClinica(request.clienteId(), clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
        Profissional profissional = atendimentoService.profissionalPermitido(clinica, request.profissionalId());

        SerieAgendamento serie = new SerieAgendamento();
        serie.setClinica(clinica);
        serie.setCliente(cliente);
        serie.setProfissional(profissional);
        serie.setFrequencia(request.frequencia());
        serie.setIncluiSabado(request.incluiSabado());
        serie.setInicio(request.inicio());
        serie.setDataFim(request.dataFim());
        serie.setQuantidade(request.quantidade());
        serieRepository.save(serie);

        // Cada sessão passa pela mesma validação (com lock) de um agendamento
        // avulso: se alguém ocupou um horário depois da prévia, a série
        // inteira é desfeita e a mensagem diz qual.
        List<CreateAtendimentoResponseDTO> criados = new ArrayList<>();
        for (OcorrenciaDTO ocorrencia : livres) {
            CreateAtendimentoRequestDTO sessao = new CreateAtendimentoRequestDTO(request.clienteId(),
                    ocorrencia.dataHora(), request.servicos(), request.profissionalId());
            criados.add(atendimentoService.createAtendimento(clinica, sessao, serie));
        }
        List<OcorrenciaDTO> puladas = ocorrencias.stream().filter(o -> !o.disponivel()).toList();
        return new SerieCriadaDTO(serie.getId(), criados, puladas);
    }

    /**
     * Cancela esta sessão e as seguintes da mesma série que ainda estão
     * agendadas. As já concluídas ficam como estão.
     */
    @Transactional
    public CancelamentoSerieDTO cancelarSeguintes(Clinica clinica, UUID atendimentoId) {
        Atendimento atendimento = atendimentoService.buscarVisivel(clinica, atendimentoId);
        if (atendimento.getSerie() == null) {
            throw new IllegalArgumentException("Este atendimento não faz parte de uma série");
        }
        Optional<UUID> restrito = clinicaContext.profissionalRestrito();
        int cancelados = 0;
        for (Atendimento sessao : atendimentoRepository
                .findBySerie_IdAndDataAtendimentoGreaterThanEqualOrderByDataAtendimentoAsc(
                        atendimento.getSerie().getId(), atendimento.getDataAtendimento())) {
            if (sessao.getStatus() != StatusAtendimento.AGENDADO) {
                continue;
            }
            // O profissional só cancela o que está na agenda dele.
            if (restrito.isPresent() && !restrito.get().equals(sessao.getProfissional().getId())) {
                continue;
            }
            atendimentoService.alterarStatus(clinica, sessao.getId(), StatusAtendimento.CANCELADO);
            cancelados++;
        }
        return new CancelamentoSerieDTO(cancelados);
    }

    private List<OcorrenciaDTO> ocorrencias(Clinica clinica, SerieRequestDTO request) {
        validarFim(request);
        List<ServicoSelecionadoDTO> selecionados = request.servicos();
        List<Servico> servicos = atendimentoService.resolverServicos(clinica, selecionados);
        int duracao = atendimentoService.duracaoTotal(servicos);
        Profissional profissional = atendimentoService.profissionalPermitido(clinica, request.profissionalId());
        clienteRepository.findByIdAndClinica(request.clienteId(), clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

        LocalDate limite = LocalDate.now(clock).plusDays(AgendaService.JANELA_INTERNA_DIAS);
        List<OcorrenciaDTO> resultado = new ArrayList<>();
        int livres = 0;
        for (LocalDateTime dataHora : candidatas(request.inicio(), request.frequencia(), MAXIMO_CANDIDATAS)) {
            LocalDate dia = dataHora.toLocalDate();
            if (dia.isAfter(limite) || (request.dataFim() != null && dia.isAfter(request.dataFim()))) {
                break;
            }
            Optional<String> pulo = puloDeFimDeSemana(dia, request.incluiSabado());
            if (pulo.isPresent()) {
                // Na diária o fim de semana simplesmente não entra; nas
                // outras, a data cairia ali e a pessoa precisa saber.
                if (request.frequencia() != FrequenciaSerie.DIARIA) {
                    resultado.add(new OcorrenciaDTO(dataHora, false, pulo.get()));
                }
                continue;
            }
            Optional<String> motivo = agendaService.motivoIndisponivel(clinica, profissional, dataHora, duracao,
                    null, AgendaService.JANELA_INTERNA_DIAS);
            resultado.add(new OcorrenciaDTO(dataHora, motivo.isEmpty(), motivo.orElse(null)));
            if (motivo.isEmpty() && ++livres == alvo(request)) {
                break;
            }
        }
        return resultado;
    }

    private int alvo(SerieRequestDTO request) {
        return request.quantidade() != null ? request.quantidade() : Integer.MAX_VALUE;
    }

    private void validarFim(SerieRequestDTO request) {
        if ((request.dataFim() == null) == (request.quantidade() == null)) {
            throw new IllegalArgumentException("Informe a data final ou a quantidade de sessões (só um dos dois)");
        }
        if (request.quantidade() != null && (request.quantidade() < 1 || request.quantidade() > MAXIMO_SESSOES)) {
            throw new IllegalArgumentException("A série deve ter entre 1 e " + MAXIMO_SESSOES + " sessões");
        }
        if (request.dataFim() != null && request.dataFim().isBefore(request.inicio().toLocalDate())) {
            throw new IllegalArgumentException("A data final não pode ser antes da primeira sessão");
        }
    }

    /**
     * Datas candidatas a partir da primeira. Mensal soma meses à data
     * inicial (e não à anterior): começando em 31/01 dá 28/02, 31/03, 30/04.
     */
    static List<LocalDateTime> candidatas(LocalDateTime inicio, FrequenciaSerie frequencia, int limite) {
        List<LocalDateTime> datas = new ArrayList<>(limite);
        for (int i = 0; i < limite; i++) {
            datas.add(switch (frequencia) {
                case DIARIA -> inicio.plusDays(i);
                case SEMANAL -> inicio.plusWeeks(i);
                case QUINZENAL -> inicio.plusWeeks(2L * i);
                case MENSAL -> inicio.plusMonths(i);
            });
        }
        return datas;
    }

    static Optional<String> puloDeFimDeSemana(LocalDate dia, boolean incluiSabado) {
        if (dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return Optional.of("Domingo");
        }
        if (dia.getDayOfWeek() == DayOfWeek.SATURDAY && !incluiSabado) {
            return Optional.of("Sábado (não incluído na série)");
        }
        return Optional.empty();
    }
}
