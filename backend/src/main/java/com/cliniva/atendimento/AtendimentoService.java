package com.cliniva.atendimento;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.AgendaService;
import com.cliniva.atendimento.dtos.AtendimentoResumoResponseDTO;
import com.cliniva.atendimento.dtos.AtendimentoResponseDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.UpdateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO.ItemUsadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO.ServicoRealizadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO.ServicoRealizadoDTO.ItemUsadoRealDTO;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoItem;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.model.SerieAgendamento;
import com.cliniva.atendimento.repository.AtendimentoItemRepository;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.TransicaoStatusInvalidaException;
import com.cliniva.item.Item;
import com.cliniva.item.ItemRepository;
import com.cliniva.pacote.PacoteService;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AtendimentoService {
        private final AtendimentoRepository atendimentoRepository;
        private final AtendimentoServicoRepository atendimentoServicoRepository;
        private final AtendimentoItemRepository atendimentoItemRepository;
        private final ClienteRepository clienteRepository;
        private final ServicoRepository servicoRepository;
        private final ItemRepository itemRepository;
        private final AgendaService agendaService;
        private final ProfissionalService profissionalService;
        private final ClinicaContext clinicaContext;
        private final PacoteService pacoteService;
        private final Clock clock;

        @Transactional
        public CreateAtendimentoResponseDTO createAtendimento(Clinica clinica,
                        CreateAtendimentoRequestDTO requestDTO) {
                return createAtendimento(clinica, requestDTO, null);
        }

        /** Cria um atendimento; {@code serie} liga a sessão à série que a gerou. */
        @Transactional
        public CreateAtendimentoResponseDTO createAtendimento(Clinica clinica, CreateAtendimentoRequestDTO requestDTO,
                        SerieAgendamento serie) {
                Cliente cliente = clienteRepository.findByIdAndClinica(requestDTO.clienteId(), clinica)
                                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

                List<ServicoSelecionadoDTO> servicosSelecionados = requestDTO.servicos();
                List<Servico> servicos = resolverServicos(clinica, servicosSelecionados);
                int duracaoTotal = duracaoTotal(servicos);
                Profissional profissional = profissionalPermitido(clinica, requestDTO.profissionalId());
                profissionalService.exigirApto(profissional, servicos);

                // validarDisponibilidade adquire o lock pessimista da clínica,
                // garantindo check + insert atômicos (evita double booking e
                // estoque negativo em agendamentos simultâneos). O conflito
                // é checado só na agenda deste profissional.
                agendaService.validarDisponibilidade(clinica, profissional, requestDTO.dataAtendimento(),
                                duracaoTotal, null, AgendaService.JANELA_INTERNA_DIAS);

                Atendimento atendimento = new Atendimento();
                atendimento.setClinica(clinica);
                atendimento.setCliente(cliente);
                atendimento.setProfissional(profissional);
                atendimento.setSerie(serie);
                atendimento.setDataAtendimento(requestDTO.dataAtendimento());
                atendimento.setDuracaoMinutos(duracaoTotal);
                atendimento.setStatus(StatusAtendimento.AGENDADO);
                // Pelo Clock da aplicação, e não pelo fuso do container: entre
                // 21h e 24h no Brasil o UTC já é o dia seguinte.
                atendimento.setDataCriacao(LocalDate.now(clock));
                atendimentoRepository.save(atendimento);

                return registrarServicos(clinica, atendimento, cliente, profissional, servicos, servicosSelecionados);
        }

        /** Serviços pedidos, da clínica e sem repetição. */
        public List<Servico> resolverServicos(Clinica clinica, List<ServicoSelecionadoDTO> servicosSelecionados) {
                List<Servico> servicos = new ArrayList<>();
                for (ServicoSelecionadoDTO servicoSelecionado : servicosSelecionados) {
                        Servico servicoEncontrado = servicoRepository
                                        .findByIdAndClinica(servicoSelecionado.servicoId(), clinica)
                                        .orElseThrow(() -> new RecursoNaoEncontradoException(
                                                        "Serviço não encontrado"));
                        if (servicos.stream().anyMatch(
                                        resolvido -> resolvido.getId().equals(servicoEncontrado.getId()))) {
                                throw new RecursoDuplicadoException("Serviço já adicionado a este atendimento.");
                        }
                        servicos.add(servicoEncontrado);
                }
                return servicos;
        }

        public int duracaoTotal(List<Servico> servicos) {
                int duracaoTotal = servicos.stream().mapToInt(Servico::getDuracaoMinutos).sum();
                if (duracaoTotal > AgendaService.DURACAO_MAXIMA_MINUTOS) {
                        throw new TransicaoStatusInvalidaException("A soma das durações dos serviços excede "
                                        + AgendaService.DURACAO_MAXIMA_MINUTOS + " minutos");
                }
                return duracaoTotal;
        }

        private CreateAtendimentoResponseDTO registrarServicos(Clinica clinica, Atendimento atendimento,
                        Cliente cliente, Profissional profissional, List<Servico> servicos,
                        List<ServicoSelecionadoDTO> servicosSelecionados) {
                List<ServicoRealizadoDTO> servicosRealizados = new ArrayList<>();

                for (int i = 0; i < servicos.size(); i++) {
                        Servico servicoEncontrado = servicos.get(i);
                        ServicoSelecionadoDTO servicoSelecionado = servicosSelecionados.get(i);

                        AtendimentoServico atendimentoServico = new AtendimentoServico();
                        atendimentoServico.setAtendimento(atendimento);
                        atendimentoServico.setServico(servicoEncontrado);
                        atendimentoServico.setValorCobrado(servicoEncontrado.getValor());
                        atendimentoServicoRepository.save(atendimentoServico);

                        List<ItemUsadoRealDTO> itensRealizados = new ArrayList<>();

                        // `itensExtras` é opcional na API: ausente precisa
                        // significar "nenhum item", não NPE. Sem este guarda,
                        // criar um atendimento sem itens extras quebrava com
                        // NullPointerException — que ainda virava 401 na
                        // resposta (ver SecurityConfig), então parecia falha
                        // de autenticação em vez de bug.
                        List<ItemUsadoDTO> itensExtras = servicoSelecionado.itensExtras() != null
                                        ? servicoSelecionado.itensExtras()
                                        : List.of();

                        for (ItemUsadoDTO itemUsadoDTO : itensExtras) {
                                Item itemEncontrado = itemRepository.findByIdAndClinica(itemUsadoDTO.itemId(),
                                                clinica)
                                                .orElseThrow(() -> new RecursoNaoEncontradoException(
                                                                "Item não encontrado"));

                                BigDecimal quantidadeNova = itemUsadoDTO.quantidade();

                                Optional<AtendimentoItem> existente = atendimentoItemRepository
                                                .findByAtendimentoServicoAndItem(atendimentoServico, itemEncontrado);

                                AtendimentoItem atendimentoItem;
                                if (existente.isPresent()) {
                                        atendimentoItem = existente.get();
                                        atendimentoItem.setQuantidadeUsada(
                                                        atendimentoItem.getQuantidadeUsada().add(quantidadeNova));
                                } else {
                                        atendimentoItem = new AtendimentoItem();
                                        atendimentoItem.setAtendimentoServico(atendimentoServico);
                                        atendimentoItem.setItem(itemEncontrado);
                                        atendimentoItem.setQuantidadeUsada(quantidadeNova);
                                }

                                // delta = só o que chegou agora, nunca o total acumulado
                                itemEncontrado.removerQuantidade(quantidadeNova);
                                itemRepository.save(itemEncontrado);
                                atendimentoItemRepository.save(atendimentoItem);

                                itensRealizados.add(new ItemUsadoRealDTO(itemEncontrado.getId(),
                                                atendimentoItem.getQuantidadeUsada()));
                        }

                        servicosRealizados.add(new ServicoRealizadoDTO(
                                        servicoEncontrado.getId(),
                                        atendimentoServico.getValorCobrado(),
                                        itensRealizados));
                }

                return new CreateAtendimentoResponseDTO(
                                atendimento.getId(),
                                cliente.getId(),
                                atendimento.getDataAtendimento(),
                                atendimento.getDataCriacao(),
                                atendimento.getDuracaoMinutos(),
                                atendimento.getStatus(),
                                servicosRealizados,
                                profissional.getId(),
                                profissional.getNome());
        }

        @Transactional
        public AtendimentoResponseDTO alterarStatus(Clinica clinica, UUID id, StatusAtendimento novoStatus) {
                Atendimento atendimento = buscarVisivel(clinica, id);

                // CANCELADO é o único estado terminal
                if (atendimento.getStatus() == StatusAtendimento.CANCELADO) {
                        throw new TransicaoStatusInvalidaException("Atendimento cancelado não pode mais ser alterado");
                }
                if (novoStatus == atendimento.getStatus()) {
                        throw new TransicaoStatusInvalidaException("Atendimento já está com status " + novoStatus);
                }

                // devolve estoque SOMENTE se o serviço nunca aconteceu;
                // CONCLUIDO → CANCELADO é correção de digitação e o produto foi usado de
                // verdade
                if (novoStatus == StatusAtendimento.CANCELADO
                                && atendimento.getStatus() == StatusAtendimento.AGENDADO) {
                        devolverEstoque(atendimento);
                }

                // Pacote: a sessão sai do saldo quando o atendimento é
                // concluído e volta se ele deixar de estar concluído.
                if (atendimento.getStatus() == StatusAtendimento.CONCLUIDO) {
                        pacoteService.estornarSessoes(atendimento);
                }

                atendimento.setStatus(novoStatus);
                atendimentoRepository.save(atendimento);

                if (novoStatus == StatusAtendimento.CONCLUIDO) {
                        pacoteService.baixarSessoes(atendimento);
                }

                return buscarPorId(clinica, id);
        }

        @Transactional
        public AtendimentoResponseDTO atualizarAtendimento(Clinica clinica, UUID id,
                        UpdateAtendimentoRequestDTO requestDTO) {
                Atendimento atendimento = buscarVisivel(clinica, id);

                if (atendimento.getStatus() != StatusAtendimento.AGENDADO) {
                        throw new TransicaoStatusInvalidaException(
                                        "Somente atendimentos com status AGENDADO podem ser alterados");
                }

                Cliente cliente = clienteRepository.findByIdAndClinica(requestDTO.clienteId(), clinica)
                                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

                Profissional profissional = atendimento.getProfissional();
                if (requestDTO.profissionalId() != null
                                && !requestDTO.profissionalId().equals(profissional.getId())) {
                        profissional = profissionalPermitido(clinica, requestDTO.profissionalId());
                        List<Servico> servicos = atendimentoServicoRepository.findByAtendimento(atendimento).stream()
                                        .map(AtendimentoServico::getServico)
                                        .toList();
                        profissionalService.exigirApto(profissional, servicos);
                }

                agendaService.validarDisponibilidade(clinica, profissional, requestDTO.dataAtendimento(),
                                atendimento.getDuracaoMinutos(), id, AgendaService.JANELA_INTERNA_DIAS);

                atendimento.setProfissional(profissional);
                atendimento.setCliente(cliente);
                atendimento.setDataAtendimento(requestDTO.dataAtendimento());
                atendimentoRepository.save(atendimento);

                return buscarPorId(clinica, id);
        }

        // cancelamento devolve ao estoque o que foi consumido nos serviços do
        // atendimento
        private void devolverEstoque(Atendimento atendimento) {
                List<AtendimentoItem> itensUsados = atendimentoItemRepository
                                .findByAtendimentoServico_Atendimento(atendimento);

                for (AtendimentoItem atendimentoItem : itensUsados) {
                        Item item = atendimentoItem.getItem();
                        item.adicionarQuantidade(atendimentoItem.getQuantidadeUsada());
                        itemRepository.save(item);
                }
        }

        @Transactional(readOnly = true)
        public List<AtendimentoResumoResponseDTO> listarAtendimentos(Clinica clinica, StatusAtendimento status,
                        UUID clienteId,
                        LocalDateTime dataInicio, LocalDateTime dataFim, UUID profissionalId) {
                UUID filtroProfissional = clinicaContext.profissionalRestrito().orElse(profissionalId);
                Specification<Atendimento> specification = AtendimentoSpecifications.comFiltros(clinica, status,
                                clienteId,
                                dataInicio, dataFim, filtroProfissional);

                return atendimentoRepository.findAll(specification).stream()
                                .map(this::toResumoResponseDTO)
                                .toList();
        }

        @Transactional(readOnly = true)
        public AtendimentoResponseDTO buscarPorId(Clinica clinica, UUID id) {
                Atendimento atendimento = buscarVisivel(clinica, id);

                List<AtendimentoResponseDTO.ServicoRealizadoDTO> servicos = atendimentoServicoRepository
                                .findByAtendimento(atendimento).stream()
                                .map(this::toServicoRealizadoDTO)
                                .toList();

                return new AtendimentoResponseDTO(
                                atendimento.getId(),
                                atendimento.getCliente().getId(),
                                atendimento.getCliente().getNome(),
                                atendimento.getDataAtendimento(),
                                atendimento.getDataCriacao(),
                                atendimento.getStatus(),
                                calcularValorTotal(atendimento),
                                servicos,
                                atendimento.getProfissional().getId(),
                                atendimento.getProfissional().getNome());
        }

        /**
         * Atendimento da clínica que o usuário pode ver. Para o PROFISSIONAL,
         * o de outro profissional responde 404, como se não existisse.
         */
        public Atendimento buscarVisivel(Clinica clinica, UUID id) {
                Atendimento atendimento = atendimentoRepository.findByIdAndClinica(id, clinica)
                                .orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado"));
                Optional<UUID> restrito = clinicaContext.profissionalRestrito();
                if (restrito.isPresent() && !restrito.get().equals(atendimento.getProfissional().getId())) {
                        throw new RecursoNaoEncontradoException("Atendimento não encontrado");
                }
                return atendimento;
        }

        /** O PROFISSIONAL só agenda na própria agenda. */
        public Profissional profissionalPermitido(Clinica clinica, UUID profissionalId) {
                Optional<UUID> restrito = clinicaContext.profissionalRestrito();
                if (restrito.isPresent() && !restrito.get().equals(profissionalId)) {
                        throw new AcessoNaoPermitidoException("Você só pode agendar na sua própria agenda");
                }
                return profissionalService.buscar(clinica, profissionalId);
        }

        private AtendimentoResumoResponseDTO toResumoResponseDTO(Atendimento atendimento) {
                return new AtendimentoResumoResponseDTO(
                                atendimento.getId(),
                                atendimento.getCliente().getId(),
                                atendimento.getCliente().getNome(),
                                atendimento.getCliente().getTelefone(),
                                atendimento.getDataAtendimento(),
                                atendimento.getStatus(),
                                calcularValorTotal(atendimento),
                                atendimento.getProfissional().getId(),
                                atendimento.getProfissional().getNome());
        }

        private AtendimentoResponseDTO.ServicoRealizadoDTO toServicoRealizadoDTO(
                        AtendimentoServico atendimentoServico) {
                List<AtendimentoResponseDTO.ServicoRealizadoDTO.ItemUsadoRealDTO> itensUsados = atendimentoItemRepository
                                .findByAtendimentoServico(atendimentoServico).stream()
                                .map(atendimentoItem -> new AtendimentoResponseDTO.ServicoRealizadoDTO.ItemUsadoRealDTO(
                                                atendimentoItem.getItem().getId(),
                                                atendimentoItem.getItem().getNome(),
                                                atendimentoItem.getQuantidadeUsada()))
                                .toList();

                return new AtendimentoResponseDTO.ServicoRealizadoDTO(
                                atendimentoServico.getServico().getId(),
                                atendimentoServico.getServico().getNome(),
                                atendimentoServico.getValorCobrado(),
                                itensUsados);
        }

        @SuppressWarnings("null")
        private BigDecimal calcularValorTotal(Atendimento atendimento) {
                return atendimentoServicoRepository.findByAtendimento(atendimento).stream()
                                .map(AtendimentoServico::getValorCobrado)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
}