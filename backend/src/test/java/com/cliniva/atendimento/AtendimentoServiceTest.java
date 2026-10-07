package com.cliniva.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.agenda.AgendaService;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO.ItemUsadoDTO;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoItem;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.repository.AtendimentoItemRepository;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.EstoqueInsuficienteException;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.TransicaoStatusInvalidaException;
import com.cliniva.item.Item;
import com.cliniva.item.ItemRepository;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;

@ExtendWith(MockitoExtension.class)
class AtendimentoServiceTest {

    @Spy
    private final Clock clock = Clock.system(ZoneId.of("America/Sao_Paulo"));

        private static final UUID ATENDIMENTO_ID = UUID.randomUUID();
        private static final UUID CLIENTE_ID = UUID.randomUUID();
        private static final UUID SERVICO_ID = UUID.randomUUID();
        private static final UUID ITEM_ID = UUID.randomUUID();

        private static final Clinica CLINICA = clinica("Clínica A");

        @Mock
        private AtendimentoRepository atendimentoRepository;
        @Mock
        private AtendimentoServicoRepository atendimentoServicoRepository;
        @Mock
        private AtendimentoItemRepository atendimentoItemRepository;
        @Mock
        private ClienteRepository clienteRepository;
        @Mock
        private ServicoRepository servicoRepository;
        @Mock
        private ItemRepository itemRepository;
        @Mock
        private AgendaService agendaService;
        @Mock
        private ProfissionalService profissionalService;
        @Mock
        private ClinicaContext clinicaContext;

        private static final UUID PROFISSIONAL_ID = UUID.randomUUID();
        private static final Profissional ANA = profissional(PROFISSIONAL_ID, "Ana");

        private static Profissional profissional(UUID id, String nome) {
                Profissional profissional = new Profissional();
                profissional.setClinica(CLINICA);
                profissional.setNome(nome);
                profissional.setAtivo(true);
                ReflectionTestUtils.setField(profissional, "id", id);
                return profissional;
        }

        @org.junit.jupiter.api.BeforeEach
        void setUpProfissional() {
                org.mockito.Mockito.lenient().when(profissionalService.buscar(CLINICA, PROFISSIONAL_ID))
                                .thenReturn(ANA);
        }

        @InjectMocks
        private AtendimentoService atendimentoService;

        private static Clinica clinica(String nome) {
                Clinica clinica = new Clinica();
                ReflectionTestUtils.setField(clinica, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(clinica, "nome", nome);
                return clinica;
        }

        private Cliente cliente(UUID id, String nome) {
                Cliente cliente = new Cliente();
                cliente.setClinica(CLINICA);
                cliente.setNome(nome);
                ReflectionTestUtils.setField(cliente, "id", id);
                return cliente;
        }

        private Servico servico(String valor) {
                Servico servico = new Servico();
                servico.setClinica(CLINICA);
                servico.setNome("Limpeza de Pele");
                servico.setValor(new BigDecimal(valor));
                ReflectionTestUtils.setField(servico, "id", SERVICO_ID);
                return servico;
        }

        private Item item(String estoqueInicial) {
                Item item = new Item();
                item.setClinica(CLINICA);
                item.setNome("Sérum Vitamina C");
                item.adicionarQuantidade(new BigDecimal(estoqueInicial));
                ReflectionTestUtils.setField(item, "id", ITEM_ID);
                return item;
        }

        private Atendimento atendimentoComStatus(StatusAtendimento status) {
                Atendimento atendimento = new Atendimento();
                atendimento.setClinica(CLINICA);
                atendimento.setProfissional(ANA);
                atendimento.setCliente(cliente(CLIENTE_ID, "Maria"));
                atendimento.setDataAtendimento(LocalDateTime.now().plusDays(1));
                atendimento.setStatus(status);
                ReflectionTestUtils.setField(atendimento, "id", ATENDIMENTO_ID);
                return atendimento;
        }

        private AtendimentoServico atendimentoServico(Atendimento atendimento, String valorCobrado) {
                AtendimentoServico atendimentoServico = new AtendimentoServico();
                atendimentoServico.setAtendimento(atendimento);
                atendimentoServico.setServico(servico(valorCobrado));
                atendimentoServico.setValorCobrado(new BigDecimal(valorCobrado));
                return atendimentoServico;
        }

        private AtendimentoItem atendimentoItem(AtendimentoServico atendimentoServico, Item item,
                        String quantidadeUsada) {
                AtendimentoItem atendimentoItem = new AtendimentoItem();
                atendimentoItem.setAtendimentoServico(atendimentoServico);
                atendimentoItem.setItem(item);
                atendimentoItem.setQuantidadeUsada(new BigDecimal(quantidadeUsada));
                return atendimentoItem;
        }

        private CreateAtendimentoRequestDTO requisicaoComUmServicoEItens(ItemUsadoDTO... itens) {
                return new CreateAtendimentoRequestDTO(
                                CLIENTE_ID,
                                LocalDateTime.now().plusDays(1),
                                List.of(new ServicoSelecionadoDTO(SERVICO_ID, List.of(itens))), PROFISSIONAL_ID);
        }

        /**
         * `itensExtras` ausente precisa significar "nenhum item". Antes isso
         * quebrava com NullPointerException — que virava 401 na resposta por
         * causa do dispatch de erro, então parecia token expirado.
         */
        @Test
        void deveCriarAtendimentoQuandoItensExtrasVemAusente() {
                Servico servico = servico("150.00");

                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico));
                when(atendimentoRepository.save(any(Atendimento.class)))
                                .thenAnswer(invocacao -> invocacao.getArgument(0));
                when(atendimentoServicoRepository.save(any(AtendimentoServico.class)))
                                .thenAnswer(invocacao -> invocacao.getArgument(0));

                var requisicao = new CreateAtendimentoRequestDTO(
                                CLIENTE_ID,
                                LocalDateTime.now().plusDays(1),
                                List.of(new ServicoSelecionadoDTO(SERVICO_ID, null)), PROFISSIONAL_ID);

                var resposta = atendimentoService.createAtendimento(CLINICA, requisicao);

                assertThat(resposta.servicos()).hasSize(1);
                assertThat(resposta.servicos().get(0).itensExtras()).isEmpty();
                verify(itemRepository, never()).findByIdAndClinica(any(), any());
        }

        // ---------- criação ----------

        @Test
        void deveCriarAtendimentoGravandoSnapshotDoValorDoServico() {
                Servico servico = servico("150.00");
                Item item = item("10");

                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico));
                when(itemRepository.findByIdAndClinica(ITEM_ID, CLINICA)).thenReturn(Optional.of(item));
                when(atendimentoItemRepository.findByAtendimentoServicoAndItem(any(), any()))
                                .thenReturn(Optional.empty());

                var resposta = atendimentoService.createAtendimento(CLINICA,
                                requisicaoComUmServicoEItens(new ItemUsadoDTO(ITEM_ID, new BigDecimal("2"))));

                assertThat(resposta.status()).isEqualTo(StatusAtendimento.AGENDADO);
                assertThat(resposta.servicos()).hasSize(1);

                ArgumentCaptor<AtendimentoServico> captor = ArgumentCaptor.forClass(AtendimentoServico.class);
                verify(atendimentoServicoRepository).save(captor.capture());
                assertThat(captor.getValue().getValorCobrado()).isEqualByComparingTo("150.00");

                assertThat(item.getQuantidadeEmEstoque()).isEqualByComparingTo("8");
                verify(itemRepository).save(item);
        }

        @Test
        void deveSomarQuantidadeQuandoMesmoItemApareceDuasVezesNoServico() {
                Item item = item("10");
                AtomicReference<AtendimentoItem> salvo = new AtomicReference<>();

                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico("100.00")));
                when(itemRepository.findByIdAndClinica(ITEM_ID, CLINICA)).thenReturn(Optional.of(item));
                when(atendimentoItemRepository.findByAtendimentoServicoAndItem(any(), any()))
                                .thenReturn(Optional.empty())
                                .thenAnswer(invocacao -> Optional.ofNullable(salvo.get()));
                when(atendimentoItemRepository.save(any(AtendimentoItem.class)))
                                .thenAnswer(invocacao -> {
                                        salvo.set(invocacao.getArgument(0));
                                        return salvo.get();
                                });

                var resposta = atendimentoService.createAtendimento(CLINICA, requisicaoComUmServicoEItens(
                                new ItemUsadoDTO(ITEM_ID, BigDecimal.ONE),
                                new ItemUsadoDTO(ITEM_ID, new BigDecimal("2"))));

                assertThat(resposta.servicos().get(0).itensExtras()).hasSize(2);
                assertThat(resposta.servicos().get(0).itensExtras().getLast().quantidadeUsada())
                                .isEqualByComparingTo("3");
                assertThat(item.getQuantidadeEmEstoque()).isEqualByComparingTo("7");
        }

        @Test
        void naoDeveCriarAtendimentoComClienteInexistente() {
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA,
                                requisicaoComUmServicoEItens(new ItemUsadoDTO(ITEM_ID, BigDecimal.ONE))))
                                .isInstanceOf(RecursoNaoEncontradoException.class)
                                .hasMessageContaining("Cliente");
        }

        @Test
        void naoDeveCriarAtendimentoComServicoInexistente() {
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA,
                                requisicaoComUmServicoEItens(new ItemUsadoDTO(ITEM_ID, BigDecimal.ONE))))
                                .isInstanceOf(RecursoNaoEncontradoException.class)
                                .hasMessageContaining("Serviço");
        }

        @Test
        void naoDeveCriarAtendimentoComItemInexistente() {
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico("100.00")));
                when(itemRepository.findByIdAndClinica(ITEM_ID, CLINICA)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA,
                                requisicaoComUmServicoEItens(new ItemUsadoDTO(ITEM_ID, BigDecimal.ONE))))
                                .isInstanceOf(RecursoNaoEncontradoException.class)
                                .hasMessageContaining("Item");
        }

        @Test
        void naoDeveAceitarOMesmoServicoDuasVezesNoAtendimento() {
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico("100.00")));

                CreateAtendimentoRequestDTO requisicao = new CreateAtendimentoRequestDTO(
                                CLIENTE_ID,
                                LocalDateTime.now().plusDays(1),
                                List.of(
                                                new ServicoSelecionadoDTO(SERVICO_ID, List.of()),
                                                new ServicoSelecionadoDTO(SERVICO_ID, List.of())), PROFISSIONAL_ID);

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA, requisicao))
                                .isInstanceOf(RecursoDuplicadoException.class);
        }

        @Test
        void naoDeveCriarAtendimentoSemEstoqueSuficiente() {
                Item item = item("1");

                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico("100.00")));
                when(itemRepository.findByIdAndClinica(ITEM_ID, CLINICA)).thenReturn(Optional.of(item));
                when(atendimentoItemRepository.findByAtendimentoServicoAndItem(any(), any()))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA,
                                requisicaoComUmServicoEItens(new ItemUsadoDTO(ITEM_ID, new BigDecimal("5")))))
                                .isInstanceOf(EstoqueInsuficienteException.class);

                assertThat(item.getQuantidadeEmEstoque()).isEqualByComparingTo("1");
        }

        // ---------- mudança de status ----------

        @Test
        void deveConcluirAtendimentoAgendado() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.AGENDADO);
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));

                var resposta = atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID, StatusAtendimento.CONCLUIDO);

                assertThat(resposta.status()).isEqualTo(StatusAtendimento.CONCLUIDO);
                verify(itemRepository, never()).save(any());
        }

        @Test
        void cancelamentoDeAtendimentoAgendadoDeveDevolverEstoque() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.AGENDADO);
                Item item = item("5");
                AtendimentoItem consumo = atendimentoItem(
                                atendimentoServico(atendimento, "100.00"), item, "2");

                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));
                when(atendimentoItemRepository.findByAtendimentoServico_Atendimento(atendimento))
                                .thenReturn(List.of(consumo));

                var resposta = atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID, StatusAtendimento.CANCELADO);

                assertThat(resposta.status()).isEqualTo(StatusAtendimento.CANCELADO);
                assertThat(item.getQuantidadeEmEstoque()).isEqualByComparingTo("7");
                verify(itemRepository).save(item);
        }

        @Test
        void correcaoDeConcluidoParaCanceladoNaoDeveDevolverEstoque() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.CONCLUIDO);

                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));

                var resposta = atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID, StatusAtendimento.CANCELADO);

                assertThat(resposta.status()).isEqualTo(StatusAtendimento.CANCELADO);
                verify(itemRepository, never()).save(any());
        }

        @Test
        void canceladoEDefinitivoENaoPodeVoltar() {
                Atendimento cancelado = atendimentoComStatus(StatusAtendimento.CANCELADO);
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(cancelado));

                assertThatThrownBy(() -> atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID,
                                StatusAtendimento.AGENDADO))
                                .isInstanceOf(TransicaoStatusInvalidaException.class)
                                .hasMessageContaining("cancelado");
        }

        @Test
        void naoDeveAlterarParaOMesmoStatusAtual() {
                Atendimento agendado = atendimentoComStatus(StatusAtendimento.AGENDADO);
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(agendado));

                assertThatThrownBy(() -> atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID,
                                StatusAtendimento.AGENDADO))
                                .isInstanceOf(TransicaoStatusInvalidaException.class);
        }

        @Test
        void naoDeveAlterarStatusDeAtendimentoInexistente() {
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID,
                                StatusAtendimento.CONCLUIDO))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void naoDeveAlterarAtendimentoDeOutraClinica() {
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID,
                                StatusAtendimento.CONCLUIDO))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        // ---------- remarcação ----------

        @Test
        void deveRemarcarDataEClienteDeAtendimentoAgendado() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.AGENDADO);
                UUID novoClienteId = UUID.randomUUID();
                LocalDateTime novaData = LocalDateTime.now().plusDays(7);

                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));
                when(clienteRepository.findByIdAndClinica(novoClienteId, CLINICA))
                                .thenReturn(Optional.of(cliente(novoClienteId, "Joana")));

                atendimentoService.atualizarAtendimento(CLINICA, ATENDIMENTO_ID,
                                new com.cliniva.atendimento.dtos.UpdateAtendimentoRequestDTO(novoClienteId, novaData, null));

                assertThat(atendimento.getDataAtendimento()).isEqualTo(novaData);
                assertThat(atendimento.getCliente().getNome()).isEqualTo("Joana");
                verify(atendimentoRepository).save(atendimento);
        }

        @Test
        void naoDeveRemarcarAtendimentoConcluido() {
                Atendimento concluido = atendimentoComStatus(StatusAtendimento.CONCLUIDO);
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(concluido));

                assertThatThrownBy(() -> atendimentoService.atualizarAtendimento(CLINICA, ATENDIMENTO_ID,
                                new com.cliniva.atendimento.dtos.UpdateAtendimentoRequestDTO(CLIENTE_ID,
                                                LocalDateTime.now(), null)))
                                .isInstanceOf(TransicaoStatusInvalidaException.class)
                                .hasMessageContaining("AGENDADO");
        }

        @Test
        void naoDeveRemarcarComClienteInexistente() {
                Atendimento agendado = atendimentoComStatus(StatusAtendimento.AGENDADO);
                UUID inexistente = UUID.randomUUID();

                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(agendado));
                when(clienteRepository.findByIdAndClinica(inexistente, CLINICA)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.atualizarAtendimento(CLINICA, ATENDIMENTO_ID,
                                new com.cliniva.atendimento.dtos.UpdateAtendimentoRequestDTO(inexistente,
                                                LocalDateTime.now(), null)))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        // ---------- leitura ----------

        @SuppressWarnings("unchecked")
        @Test
        void deveListarResumosComValorTotalCalculado() {
                Atendimento primeiro = atendimentoComStatus(StatusAtendimento.AGENDADO);
                Atendimento segundo = atendimentoComStatus(StatusAtendimento.CONCLUIDO);

                when(atendimentoRepository.findAll(any(Specification.class)))
                                .thenReturn(List.of(primeiro, segundo));
                when(atendimentoServicoRepository.findByAtendimento(primeiro))
                                .thenReturn(List.of(
                                                atendimentoServico(primeiro, "60.00"),
                                                atendimentoServico(primeiro, "40.00")));
                when(atendimentoServicoRepository.findByAtendimento(segundo))
                                .thenReturn(List.of(atendimentoServico(segundo, "25.00")));

                var lista = atendimentoService.listarAtendimentos(
                                CLINICA, StatusAtendimento.AGENDADO, null, null, null, null);

                assertThat(lista).hasSize(2);
                assertThat(lista.get(0).nomeCliente()).isEqualTo("Maria");
                assertThat(lista.get(0).valorTotal()).isEqualByComparingTo("100.00");
                assertThat(lista.get(1).valorTotal()).isEqualByComparingTo("25.00");
        }

        @Test
        void deveRetornarDetalheAninhadoComItensUsados() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.AGENDADO);
                Item item = item("10");
                AtendimentoServico atendimentoServico = atendimentoServico(atendimento, "100.00");
                AtendimentoItem consumo = atendimentoItem(atendimentoServico, item, "3");

                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));
                when(atendimentoServicoRepository.findByAtendimento(atendimento))
                                .thenReturn(List.of(atendimentoServico));
                when(atendimentoItemRepository.findByAtendimentoServico(atendimentoServico))
                                .thenReturn(List.of(consumo));

                var resposta = atendimentoService.buscarPorId(CLINICA, ATENDIMENTO_ID);

                assertThat(resposta.nomeCliente()).isEqualTo("Maria");
                assertThat(resposta.valorTotal()).isEqualByComparingTo("100.00");
                assertThat(resposta.servicos()).hasSize(1);
                assertThat(resposta.servicos().get(0).nomeServico()).isEqualTo("Limpeza de Pele");
                assertThat(resposta.servicos().get(0).itensUsados().get(0).nomeItem())
                                .isEqualTo("Sérum Vitamina C");
                assertThat(resposta.servicos().get(0).itensUsados().get(0).quantidadeUsada())
                                .isEqualByComparingTo("3");
        }

        @Test
        void naoDeveBuscarAtendimentoInexistente() {
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> atendimentoService.buscarPorId(CLINICA, ATENDIMENTO_ID))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        // ---------- profissional (Fase 2) ----------

        @Test
        void criacaoConfereAAgendaDoProfissionalEscolhido() {
                Servico servico = servico("150.00");
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico));
                when(atendimentoRepository.save(any(Atendimento.class)))
                                .thenAnswer(invocacao -> invocacao.getArgument(0));
                when(atendimentoServicoRepository.save(any(AtendimentoServico.class)))
                                .thenAnswer(invocacao -> invocacao.getArgument(0));

                var resposta = atendimentoService.createAtendimento(CLINICA, requisicaoComUmServicoEItens());

                verify(profissionalService).exigirApto(ANA, List.of(servico));
                verify(agendaService).validarDisponibilidade(eq(CLINICA), eq(ANA), any(), eq(30), isNull());
                assertThat(resposta.profissionalNome()).isEqualTo("Ana");
        }

        @Test
        void profissionalLogadoNaoAgendaNaAgendaDeOutro() {
                when(clinicaContext.profissionalRestrito()).thenReturn(Optional.of(UUID.randomUUID()));
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(servicoRepository.findByIdAndClinica(SERVICO_ID, CLINICA)).thenReturn(Optional.of(servico("100.00")));

                assertThatThrownBy(() -> atendimentoService.createAtendimento(CLINICA, requisicaoComUmServicoEItens()))
                                .isInstanceOf(AcessoNaoPermitidoException.class);
                verify(atendimentoRepository, never()).save(any());
        }

        @Test
        void profissionalLogadoNaoVeAtendimentoDeOutro() {
                when(clinicaContext.profissionalRestrito()).thenReturn(Optional.of(UUID.randomUUID()));
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimentoComStatus(StatusAtendimento.AGENDADO)));

                assertThatThrownBy(() -> atendimentoService.buscarPorId(CLINICA, ATENDIMENTO_ID))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
                assertThatThrownBy(() -> atendimentoService.alterarStatus(CLINICA, ATENDIMENTO_ID,
                                StatusAtendimento.CONCLUIDO))
                                .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void remarcarParaOutroProfissionalConfereSeEleFazOServico() {
                Atendimento atendimento = atendimentoComStatus(StatusAtendimento.AGENDADO);
                UUID biaId = UUID.randomUUID();
                Profissional bia = profissional(biaId, "Bia");
                AtendimentoServico feito = atendimentoServico(atendimento, "100.00");
                when(atendimentoRepository.findByIdAndClinica(ATENDIMENTO_ID, CLINICA))
                                .thenReturn(Optional.of(atendimento));
                when(clienteRepository.findByIdAndClinica(CLIENTE_ID, CLINICA))
                                .thenReturn(Optional.of(cliente(CLIENTE_ID, "Maria")));
                when(profissionalService.buscar(CLINICA, biaId)).thenReturn(bia);
                when(atendimentoServicoRepository.findByAtendimento(atendimento)).thenReturn(List.of(feito));

                atendimentoService.atualizarAtendimento(CLINICA, ATENDIMENTO_ID,
                                new com.cliniva.atendimento.dtos.UpdateAtendimentoRequestDTO(CLIENTE_ID,
                                                atendimento.getDataAtendimento(), biaId));

                verify(profissionalService).exigirApto(bia, List.of(feito.getServico()));
                verify(agendaService).validarDisponibilidade(eq(CLINICA), eq(bia), any(), any(Integer.class),
                                eq(ATENDIMENTO_ID));
                assertThat(atendimento.getProfissional()).isSameAs(bia);
        }
}
