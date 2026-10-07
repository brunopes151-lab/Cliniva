package com.cliniva.pacote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.TransicaoStatusInvalidaException;
import com.cliniva.pacote.dtos.PacoteDtos.MovimentoPacoteDTO;
import com.cliniva.pacote.dtos.PacoteDtos.PacoteClienteResponseDTO;
import com.cliniva.pacote.dtos.PacoteDtos.PacoteResponseDTO;
import com.cliniva.pacote.dtos.PacoteDtos.SalvarPacoteRequestDTO;
import com.cliniva.pacote.dtos.PacoteDtos.VenderPacoteRequestDTO;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;

import lombok.RequiredArgsConstructor;

/**
 * Pacotes de sessões: os modelos que a clínica vende, os pacotes de cada
 * paciente e o saldo, que baixa sozinho quando um atendimento do serviço é
 * concluído e volta quando ele deixa de estar concluído.
 */
@Service
@RequiredArgsConstructor
public class PacoteService {

    private final PacoteRepository pacoteRepository;
    private final PacoteClienteRepository pacoteClienteRepository;
    private final PacoteMovimentoRepository movimentoRepository;
    private final ServicoRepository servicoRepository;
    private final ClienteRepository clienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoServicoRepository atendimentoServicoRepository;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    // ------------------------------------------------------------------
    // Modelos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PacoteResponseDTO> listarModelos(Clinica clinica) {
        return pacoteRepository.findByClinica_IdOrderByNomeAsc(clinica.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PacoteResponseDTO criarModelo(Clinica clinica, SalvarPacoteRequestDTO request) {
        String nome = request.nome().trim();
        if (pacoteRepository.existsByClinica_IdAndNomeIgnoreCase(clinica.getId(), nome)) {
            throw new RecursoDuplicadoException("Já existe um pacote com esse nome");
        }
        Pacote pacote = new Pacote();
        pacote.setClinica(clinica);
        aplicar(clinica, pacote, request, nome);
        return toResponse(pacoteRepository.save(pacote));
    }

    @Transactional
    public PacoteResponseDTO atualizarModelo(Clinica clinica, UUID id, SalvarPacoteRequestDTO request) {
        Pacote pacote = pacoteRepository.findByIdAndClinica_Id(id, clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pacote não encontrado"));
        String nome = request.nome().trim();
        if (pacoteRepository.existsByClinica_IdAndNomeIgnoreCaseAndIdNot(clinica.getId(), nome, id)) {
            throw new RecursoDuplicadoException("Já existe um pacote com esse nome");
        }
        // Os pacotes já vendidos guardam a própria cópia; mudar o modelo só
        // vale para as próximas vendas.
        aplicar(clinica, pacote, request, nome);
        return toResponse(pacoteRepository.save(pacote));
    }

    private void aplicar(Clinica clinica, Pacote pacote, SalvarPacoteRequestDTO request, String nome) {
        Servico servico = servicoRepository.findByIdAndClinica(request.servicoId(), clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
        pacote.setNome(nome);
        pacote.setServico(servico);
        pacote.setSessoes(request.sessoes());
        pacote.setValidadeDias(request.validadeDias());
        pacote.setPreco(request.preco());
        if (request.ativo() != null) {
            pacote.setAtivo(request.ativo());
        }
    }

    // ------------------------------------------------------------------
    // Pacotes do paciente
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PacoteClienteResponseDTO> listarDoCliente(Clinica clinica, UUID clienteId) {
        clienteVisivel(clinica, clienteId);
        return pacoteClienteRepository
                .findByClinica_IdAndCliente_IdOrderByDataCompraDesc(clinica.getId(), clienteId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PacoteClienteResponseDTO vender(Clinica clinica, UUID clienteId, VenderPacoteRequestDTO request) {
        Cliente cliente = clienteVisivel(clinica, clienteId);
        Pacote pacote = pacoteRepository.findByIdAndClinica_Id(request.pacoteId(), clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pacote não encontrado"));
        if (!pacote.isAtivo()) {
            throw new TransicaoStatusInvalidaException("Este pacote não está mais à venda");
        }
        LocalDate compra = request.dataCompra() != null ? request.dataCompra() : LocalDate.now(clock);
        BigDecimal valor = request.valorPago() != null ? request.valorPago() : pacote.getPreco();

        PacoteCliente pc = new PacoteCliente();
        pc.setClinica(clinica);
        pc.setCliente(cliente);
        pc.setPacote(pacote);
        pc.setServico(pacote.getServico());
        pc.setNome(pacote.getNome());
        pc.setSessoesTotal(pacote.getSessoes());
        pc.setSaldo(pacote.getSessoes());
        pc.setDataCompra(compra);
        pc.setDataValidade(compra.plusDays(pacote.getValidadeDias()));
        pc.setValorPago(valor);
        pc.setStatus(StatusPacoteCliente.ATIVO);
        return toResponse(pacoteClienteRepository.save(pc));
    }

    @Transactional
    public PacoteClienteResponseDTO cancelar(Clinica clinica, UUID clienteId, UUID pacoteClienteId) {
        clienteVisivel(clinica, clienteId);
        PacoteCliente pc = pacoteClienteRepository.findByIdAndClinica_Id(pacoteClienteId, clinica.getId())
                .filter(p -> p.getCliente().getId().equals(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pacote não encontrado"));
        if (pc.getStatus() == StatusPacoteCliente.CANCELADO) {
            throw new TransicaoStatusInvalidaException("Pacote já está cancelado");
        }
        pc.setStatus(StatusPacoteCliente.CANCELADO);
        return toResponse(pacoteClienteRepository.save(pc));
    }

    // ------------------------------------------------------------------
    // Saldo (chamado pelo AtendimentoService na mudança de status)
    // ------------------------------------------------------------------

    /**
     * Atendimento concluído: uma sessão de cada serviço dele sai do pacote do
     * paciente que vence primeiro. Pacote vencido na data do atendimento,
     * cancelado ou sem saldo não dá baixa; sem pacote, nada acontece.
     */
    @Transactional
    public void baixarSessoes(Atendimento atendimento) {
        Clinica clinica = atendimento.getClinica();
        LocalDate dia = atendimento.getDataAtendimento().toLocalDate();
        for (AtendimentoServico as : atendimentoServicoRepository.findByAtendimento(atendimento)) {
            List<PacoteCliente> candidatos = pacoteClienteRepository
                    .findByClinica_IdAndCliente_IdAndServico_IdAndStatusOrderByDataValidadeAscDataCompraAsc(
                            clinica.getId(), atendimento.getCliente().getId(), as.getServico().getId(),
                            StatusPacoteCliente.ATIVO);
            for (PacoteCliente pc : candidatos) {
                if (pc.usavelEm(dia) && pacoteClienteRepository.debitarSessao(pc.getId()) == 1) {
                    registrar(clinica, pc, atendimento, TipoMovimentoPacote.BAIXA);
                    break;
                }
            }
        }
    }

    /**
     * O atendimento deixou de estar concluído (cancelado ou voltou para
     * agendado): devolve cada sessão que ele tinha baixado e ainda não foi
     * devolvida.
     */
    @Transactional
    public void estornarSessoes(Atendimento atendimento) {
        Map<UUID, Integer> pendentes = new LinkedHashMap<>();
        Map<UUID, PacoteCliente> pacotes = new HashMap<>();
        for (PacoteMovimento m : movimentoRepository.findByAtendimento_IdOrderByCriadoEmAsc(atendimento.getId())) {
            UUID id = m.getPacoteCliente().getId();
            pacotes.put(id, m.getPacoteCliente());
            pendentes.merge(id, m.getTipo() == TipoMovimentoPacote.BAIXA ? 1 : -1, Integer::sum);
        }
        pendentes.forEach((id, quantidade) -> {
            for (int i = 0; i < quantidade; i++) {
                if (pacoteClienteRepository.devolverSessao(id) == 1) {
                    registrar(atendimento.getClinica(), pacotes.get(id), atendimento, TipoMovimentoPacote.ESTORNO);
                }
            }
        });
    }

    private void registrar(Clinica clinica, PacoteCliente pc, Atendimento atendimento, TipoMovimentoPacote tipo) {
        PacoteMovimento m = new PacoteMovimento();
        m.setClinica(clinica);
        m.setPacoteCliente(pc);
        m.setAtendimento(atendimento);
        m.setTipo(tipo);
        m.setCriadoEm(LocalDateTime.now(clock));
        movimentoRepository.save(m);
    }

    /** Mesma regra de visibilidade do cadastro de pacientes. */
    private Cliente clienteVisivel(Clinica clinica, UUID clienteId) {
        Cliente cliente = clienteRepository.findByIdAndClinica(clienteId, clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
        Optional<UUID> restrito = clinicaContext.profissionalRestrito();
        if (restrito.isPresent()
                && !atendimentoRepository.existsByCliente_IdAndProfissional_Id(clienteId, restrito.get())) {
            throw new RecursoNaoEncontradoException("Cliente não encontrado");
        }
        return cliente;
    }

    private PacoteResponseDTO toResponse(Pacote p) {
        return new PacoteResponseDTO(p.getId(), p.getNome(), p.getServico().getId(), p.getServico().getNome(),
                p.getSessoes(), p.getValidadeDias(), p.getPreco(), p.isAtivo());
    }

    private PacoteClienteResponseDTO toResponse(PacoteCliente pc) {
        List<MovimentoPacoteDTO> movimentos = pc.getId() == null ? List.of()
                : movimentoRepository.findByPacoteCliente_IdOrderByCriadoEmDesc(pc.getId()).stream()
                        .map(m -> new MovimentoPacoteDTO(m.getTipo(), m.getCriadoEm(),
                                m.getAtendimento() != null ? m.getAtendimento().getId() : null,
                                m.getAtendimento() != null ? m.getAtendimento().getDataAtendimento() : null))
                        .toList();
        return new PacoteClienteResponseDTO(pc.getId(), pc.getPacote().getId(), pc.getNome(),
                pc.getServico().getId(), pc.getServico().getNome(), pc.getSessoesTotal(), pc.getSaldo(),
                pc.getDataCompra(), pc.getDataValidade(), pc.getValorPago(), pc.situacao(LocalDate.now(clock)),
                movimentos);
    }
}
