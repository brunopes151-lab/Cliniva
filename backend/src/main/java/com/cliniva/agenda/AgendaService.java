package com.cliniva.agenda;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.dtos.AgendaItemDTO;
import com.cliniva.agenda.dtos.DisponibilidadeDiaDTO;
import com.cliniva.agenda.dtos.HorarioRequestDTO;
import com.cliniva.agenda.dtos.HorarioResponseDTO;
import com.cliniva.agenda.model.HorarioAtendimento;
import com.cliniva.agenda.model.HorarioAtendimentoId;
import com.cliniva.agenda.repository.HorarioAtendimentoRepository;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.exception.HorarioIndisponivelException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.ClinicaRepository;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgendaService {

    /** Teto de duração de um atendimento (1 dia) — evita wrap de LocalTime e abuse. */
    public static final int DURACAO_MAXIMA_MINUTOS = 1440;

    /** Janela do agendamento online (o paciente marca sozinho). */
    public static final int JANELA_DIAS = 30;

    /**
     * Janela da equipe (agenda interna e séries). Maior que a online porque
     * um tratamento de fisioterapia em série passa de um mês.
     */
    public static final int JANELA_INTERNA_DIAS = 180;

    private static final int PASSO_MINUTOS = 30;

    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoServicoRepository atendimentoServicoRepository;
    private final ServicoRepository servicoRepository;
    private final HorarioAtendimentoRepository horarioRepository;
    private final ClinicaRepository clinicaRepository;
    private final ProfissionalService profissionalService;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    /**
     * Atendimentos do dia. {@code profissionalId} filtra por quem atende;
     * um usuário PROFISSIONAL sempre vê só a própria agenda, seja qual for
     * o filtro pedido.
     */
    @Transactional(readOnly = true)
    public List<AgendaItemDTO> listarDia(Clinica clinica, LocalDate data, UUID profissionalId) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.plusDays(1).atStartOfDay();
        UUID filtro = clinicaContext.profissionalRestrito().orElse(profissionalId);

        return atendimentoRepository.findPorIntervalo(clinica, inicio, fim).stream()
                .filter(a -> filtro == null || a.getProfissional().getId().equals(filtro))
                .sorted(Comparator.comparing(Atendimento::getDataAtendimento))
                .map(this::toAgendaItemDTO)
                .toList();
    }

    /**
     * Horários livres no dia para o serviço.
     *
     * <p>Com {@code profissionalId}, só os horários daquele profissional.
     * Sem ele ("sem preferência"), a união dos horários de todos os
     * profissionais ativos que fazem o serviço.
     */
    @Transactional(readOnly = true)
    public DisponibilidadeDiaDTO disponibilidadeDia(Clinica clinica, LocalDate data, UUID servicoId,
            UUID profissionalId) {
        return disponibilidadeDia(clinica, data, servicoId, profissionalId, JANELA_DIAS);
    }

    @Transactional(readOnly = true)
    public DisponibilidadeDiaDTO disponibilidadeDia(Clinica clinica, LocalDate data, UUID servicoId,
            UUID profissionalId, int janelaDias) {
        Servico servico = servicoRepository.findByIdAndClinica(servicoId, clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        int duracao = duracaoValida(servico.getDuracaoMinutos());

        // Regra de janela: nunca sugerir horário que não pode ser agendado.
        LocalDate hoje = LocalDate.now(clock);
        if (data.isBefore(hoje) || data.isAfter(hoje.plusDays(janelaDias))) {
            return new DisponibilidadeDiaDTO(data, duracao, List.of());
        }

        List<Profissional> candidatos;
        if (profissionalId != null) {
            Profissional profissional = profissionalService.buscar(clinica, profissionalId);
            profissionalService.exigirApto(profissional, List.of(servico));
            candidatos = List.of(profissional);
        } else {
            candidatos = profissionalService.aptos(clinica, List.of(servico));
        }

        Set<LocalTime> livres = new TreeSet<>();
        for (Profissional profissional : candidatos) {
            livres.addAll(horariosLivres(clinica, profissional, data, duracao));
        }
        return new DisponibilidadeDiaDTO(data, duracao, List.copyOf(livres));
    }

    private List<LocalTime> horariosLivres(Clinica clinica, Profissional profissional, LocalDate data,
            int duracao) {
        Optional<HorarioAtendimento> horario = horarioDoDia(clinica, profissional, data);
        if (horario.isEmpty()) {
            return List.of();
        }

        List<Atendimento> ocupados = atendimentosBloqueantes(clinica, profissional, data, null);
        List<LocalTime> livres = new ArrayList<>();

        LocalDateTime abertura = data.atTime(horario.get().getAbertura());
        LocalDateTime fechamento = data.atTime(horario.get().getFechamento());
        LocalDateTime agora = LocalDateTime.now(clock);

        LocalDateTime candidato = abertura;
        while (true) {
            LocalDateTime fim = candidato.plusMinutes(duracao);
            if (fim.isAfter(fechamento)) {
                break;
            }
            if (!candidato.isBefore(agora) && !estaOcupado(candidato, duracao, ocupados)) {
                livres.add(candidato.toLocalTime());
            }
            candidato = candidato.plusMinutes(PASSO_MINUTOS);
        }
        return livres;
    }

    /**
     * "Sem preferência": o primeiro profissional (por nome) que faz todos os
     * serviços e está livre no horário. Roda sob o mesmo lock de
     * {@link #validarDisponibilidade}.
     */
    @Transactional
    public Profissional escolherLivre(Clinica clinica, List<Servico> servicos, LocalDateTime inicio,
            int duracaoMinutos) {
        List<Profissional> aptos = profissionalService.aptos(clinica, servicos);
        if (aptos.isEmpty()) {
            throw new HorarioIndisponivelException("Nenhum profissional realiza este serviço");
        }
        HorarioIndisponivelException ultimoMotivo = null;
        for (Profissional profissional : aptos) {
            try {
                validarDisponibilidade(clinica, profissional, inicio, duracaoMinutos, null);
                return profissional;
            } catch (HorarioIndisponivelException ex) {
                ultimoMotivo = ex;
            }
        }
        throw ultimoMotivo;
    }

    /**
     * Confere janela, expediente do profissional e conflito com a agenda
     * DELE. Dois profissionais diferentes podem atender no mesmo horário.
     * Janela do agendamento online; a equipe usa a variante com janela.
     */
    @Transactional
    public void validarDisponibilidade(Clinica clinica, Profissional profissional, LocalDateTime inicio,
            int duracaoMinutos, UUID atendimentoExcecaoId) {
        validarDisponibilidade(clinica, profissional, inicio, duracaoMinutos, atendimentoExcecaoId, JANELA_DIAS);
    }

    @Transactional
    public void validarDisponibilidade(Clinica clinica, Profissional profissional, LocalDateTime inicio,
            int duracaoMinutos, UUID atendimentoExcecaoId, int janelaDias) {
        int duracao = duracaoValida(duracaoMinutos);

        // Serializa todas as escritas de atendimento desta clínica (create, update e
        // consumo de estoque) evitando double booking e estoque negativo. O
        // lock continua sendo da clínica (e não do profissional) porque o
        // estoque é compartilhado.
        clinicaRepository.findByIdParaUpdate(clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Clínica não encontrada"));

        Optional<String> motivo = motivoIndisponivel(clinica, profissional, inicio, duracao, atendimentoExcecaoId,
                janelaDias);
        if (motivo.isPresent()) {
            throw new HorarioIndisponivelException(motivo.get());
        }
    }

    /**
     * Mesma conferência de {@link #validarDisponibilidade}, sem lock e sem
     * exceção: devolve o motivo quando o horário não serve. Usado na prévia
     * das séries, que lista os dias com problema antes de confirmar.
     */
    @Transactional(readOnly = true)
    public Optional<String> motivoIndisponivel(Clinica clinica, Profissional profissional, LocalDateTime inicio,
            int duracaoMinutos, UUID atendimentoExcecaoId, int janelaDias) {
        int duracao = duracaoValida(duracaoMinutos);
        LocalDate data = inicio.toLocalDate();
        LocalDate hoje = LocalDate.now(clock);

        if (data.isBefore(hoje)) {
            return Optional.of("Não é possível agendar em uma data passada");
        }
        if (data.isAfter(hoje.plusDays(janelaDias))) {
            return Optional.of("Só é possível agendar entre hoje e os próximos " + janelaDias + " dias");
        }

        Optional<HorarioAtendimento> horario = horarioDoDia(clinica, profissional, data);
        if (horario.isEmpty()) {
            return Optional.of(profissional.getNome() + " não atende neste dia");
        }

        LocalDateTime abertura = data.atTime(horario.get().getAbertura());
        LocalDateTime fechamento = data.atTime(horario.get().getFechamento());
        LocalDateTime fim = inicio.plusMinutes(duracao);

        if (inicio.isBefore(abertura) || fim.isAfter(fechamento)) {
            return Optional.of("Fora do horário de atendimento de " + profissional.getNome());
        }

        if (inicio.isBefore(LocalDateTime.now(clock))) {
            return Optional.of("Horário escolhido já passou");
        }

        List<Atendimento> ocupados = atendimentosBloqueantes(clinica, profissional, data, atendimentoExcecaoId);
        if (estaOcupado(inicio, duracao, ocupados)) {
            return Optional.of("Horário já ocupado na agenda de " + profissional.getNome());
        }
        return Optional.empty();
    }

    /**
     * Expediente de um profissional. Sem {@code profissionalId}, o do
     * "Geral", que é o expediente padrão da clínica e o modelo copiado para
     * cada profissional novo.
     */
    @Transactional(readOnly = true)
    public List<HorarioResponseDTO> listarHorarios(Clinica clinica, UUID profissionalId) {
        return horariosDe(clinica, profissionalOuGeral(clinica, profissionalId));
    }

    private List<HorarioResponseDTO> horariosDe(Clinica clinica, Profissional profissional) {
        return horarioRepository
                .findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(clinica, profissional.getId()).stream()
                .map(this::toHorarioResponseDTO)
                .toList();
    }

    @Transactional
    public List<HorarioResponseDTO> atualizarHorarios(Clinica clinica, UUID profissionalId,
            List<HorarioRequestDTO> horarios) {
        if (horarios == null || horarios.isEmpty() || horarios.size() > 7) {
            throw new IllegalArgumentException("Informe entre 1 e 7 dias de expediente");
        }
        Profissional profissional = profissionalOuGeral(clinica, profissionalId);
        Set<Integer> dias = new HashSet<>();
        for (HorarioRequestDTO request : horarios) {
            if (request.diaSemana() == null) {
                throw new IllegalArgumentException("Dia da semana é obrigatório");
            }
            if (!dias.add(request.diaSemana())) {
                throw new IllegalArgumentException("Dia da semana duplicado: " + request.diaSemana());
            }
            if (!request.abertura().isBefore(request.fechamento())) {
                throw new IllegalArgumentException("Abertura deve ser antes do fechamento");
            }
            salvarHorario(clinica, profissional, request.diaSemana(), request.abertura(),
                    request.fechamento(), request.ativoOuPadrao());
        }
        return horariosDe(clinica, profissional);
    }

    /** Profissional novo começa com o mesmo expediente do "Geral". */
    @Transactional
    public void copiarExpedienteGeral(Clinica clinica, Profissional novo) {
        Profissional geral = profissionalService.garantirGeral(clinica);
        for (HorarioAtendimento modelo : horarioRepository
                .findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(clinica, geral.getId())) {
            HorarioAtendimentoId id = new HorarioAtendimentoId(clinica.getId(), novo.getId(),
                    modelo.getId().getDiaSemana());
            if (!horarioRepository.existsById(id)) {
                salvarHorario(clinica, novo, modelo.getId().getDiaSemana(), modelo.getAbertura(),
                        modelo.getFechamento(), modelo.isAtivo());
            }
        }
    }

    private void salvarHorario(Clinica clinica, Profissional profissional, Integer diaSemana,
            LocalTime abertura, LocalTime fechamento, boolean ativo) {
        HorarioAtendimentoId id = new HorarioAtendimentoId(clinica.getId(), profissional.getId(), diaSemana);
        HorarioAtendimento horario = horarioRepository.findById(id).orElseGet(() -> {
            HorarioAtendimento novo = new HorarioAtendimento();
            novo.setId(id);
            novo.setClinica(clinica);
            novo.setProfissional(profissional);
            return novo;
        });
        horario.setAbertura(abertura);
        horario.setFechamento(fechamento);
        horario.setAtivo(ativo);
        horarioRepository.save(horario);
    }

    private Profissional profissionalOuGeral(Clinica clinica, UUID profissionalId) {
        return profissionalId == null
                ? profissionalService.garantirGeral(clinica)
                : profissionalService.buscar(clinica, profissionalId);
    }

    /**
     * Expediente padrão de segunda a sábado, 08:00–18:00, atribuído ao
     * profissional "Geral" da clínica.
     *
     * <p>O profissional é obrigatório desde a migration 08: a chave primária
     * de {@code horario_atendimento} passou a incluir
     * {@code profissional_id}, então o Postgres recusa INSERT sem ele. Sem
     * este passo, {@code POST /api/public/onboarding} falha — é o que
     * destrava a criação de clínica.
     */
    @Transactional
    public void semearPadrao(Clinica clinica) {
        Profissional geral = profissionalService.garantirGeral(clinica);
        for (int dia = 1; dia <= 6; dia++) {
            HorarioAtendimentoId id = new HorarioAtendimentoId(clinica.getId(), geral.getId(), dia);
            if (horarioRepository.existsById(id)) {
                continue;
            }
            HorarioAtendimento horario = new HorarioAtendimento();
            horario.setId(id);
            horario.setClinica(clinica);
            horario.setProfissional(geral);
            horario.setAbertura(LocalTime.of(8, 0));
            horario.setFechamento(LocalTime.of(18, 0));
            horario.setAtivo(true);
            horarioRepository.save(horario);
        }
    }

    private Optional<HorarioAtendimento> horarioDoDia(Clinica clinica, Profissional profissional,
            LocalDate data) {
        return horarioRepository
                .findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(clinica, profissional.getId()).stream()
                .filter(h -> h.isAtivo() && h.getId().getDiaSemana().equals(data.getDayOfWeek().getValue()))
                .findFirst();
    }

    /**
     * Atendimentos que bloqueiam o dia: mesmo profissional, status diferente de
     * CANCELADO e início na janela [ontem 00:00, amanhã 00:00) — a janela
     * estendida captura atendimentos que começam no dia anterior e terminam no
     * dia corrente. A sobreposição real é checada em memória por intervalo.
     */
    private List<Atendimento> atendimentosBloqueantes(Clinica clinica, Profissional profissional,
            LocalDate data, UUID excecaoId) {
        LocalDateTime inicio = data.minusDays(1).atStartOfDay();
        LocalDateTime fim = data.plusDays(1).atStartOfDay();
        return atendimentoRepository.findPorIntervalo(clinica, inicio, fim).stream()
                .filter(a -> a.getStatus() != StatusAtendimento.CANCELADO)
                .filter(a -> a.getProfissional() != null
                        && a.getProfissional().getId().equals(profissional.getId()))
                .filter(a -> excecaoId == null || !a.getId().equals(excecaoId))
                .toList();
    }

    private boolean estaOcupado(LocalDateTime inicio, int duracaoMinutos, List<Atendimento> ocupados) {
        LocalDateTime fim = inicio.plusMinutes(duracaoMinutos);
        for (Atendimento outro : ocupados) {
            int duracaoOutro = Math.min(outro.getDuracaoMinutos(), DURACAO_MAXIMA_MINUTOS);
            LocalDateTime outroFim = outro.getDataAtendimento().plusMinutes(duracaoOutro);
            if (inicio.isBefore(outroFim) && outro.getDataAtendimento().isBefore(fim)) {
                return true;
            }
        }
        return false;
    }

    private int duracaoValida(int duracaoMinutos) {
        if (duracaoMinutos <= 0 || duracaoMinutos > DURACAO_MAXIMA_MINUTOS) {
            throw new HorarioIndisponivelException(
                    "Duração do atendimento deve estar entre 1 e " + DURACAO_MAXIMA_MINUTOS + " minutos");
        }
        return duracaoMinutos;
    }

    private AgendaItemDTO toAgendaItemDTO(Atendimento atendimento) {
        List<AtendimentoServico> servicos = atendimentoServicoRepository.findByAtendimento(atendimento);
        List<String> nomes = servicos.stream().map(s -> s.getServico().getNome()).toList();
        BigDecimal valorTotal = servicos.stream()
                .map(AtendimentoServico::getValorCobrado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        UUID clienteId = atendimento.getCliente() != null ? atendimento.getCliente().getId() : null;
        String clienteNome = atendimento.getCliente() != null ? atendimento.getCliente().getNome() : "";
        String clienteTelefone = atendimento.getCliente() != null ? atendimento.getCliente().getTelefone() : "";

        return new AgendaItemDTO(
                atendimento.getId(),
                clienteId,
                clienteNome,
                clienteTelefone,
                atendimento.getDataAtendimento(),
                atendimento.getDataAtendimento().plusMinutes(atendimento.getDuracaoMinutos()),
                atendimento.getDuracaoMinutos(),
                atendimento.getStatus(),
                valorTotal,
                nomes,
                atendimento.getProfissional().getId(),
                atendimento.getProfissional().getNome(),
                atendimento.getProfissional().getCor(),
                atendimento.getSerie() != null ? atendimento.getSerie().getId() : null);
    }

    private HorarioResponseDTO toHorarioResponseDTO(HorarioAtendimento horario) {
        return new HorarioResponseDTO(
                horario.getId().getDiaSemana(),
                horario.getAbertura(),
                horario.getFechamento(),
                horario.isAtivo());
    }
}
