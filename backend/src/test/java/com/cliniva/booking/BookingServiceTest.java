package com.cliniva.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.agenda.AgendaService;
import com.cliniva.atendimento.AtendimentoService;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO.ServicoRealizadoDTO;
import com.cliniva.atendimento.enums.StatusAtendimento;
import com.cliniva.booking.dtos.BookingRequestDTO;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.cliente.enums.OrigemCliente;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaRepository;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String SLUG = "clinica-a";

    private static final Clinica CLINICA = clinica();

    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private AgendaService agendaService;
    @Mock
    private AtendimentoService atendimentoService;
    @Mock
    private ProfissionalService profissionalService;

    private static final UUID PROFISSIONAL_ID = UUID.randomUUID();

    @InjectMocks
    private BookingService bookingService;

    private static Clinica clinica() {
        Clinica clinica = new Clinica();
        ReflectionTestUtils.setField(clinica, "id", UUID.randomUUID());
        clinica.setNome("Clínica A");
        clinica.setSlug(SLUG);
        return clinica;
    }

    private Servico servico() {
        Servico servico = new Servico();
        ReflectionTestUtils.setField(servico, "id", UUID.randomUUID());
        servico.setClinica(CLINICA);
        servico.setNome("Limpeza de Pele");
        servico.setValor(new BigDecimal("120.00"));
        servico.setDuracaoMinutos(45);
        return servico;
    }

    private BookingRequestDTO requisicao(LocalDate data) {
        return new BookingRequestDTO(UUID.randomUUID(),
                data.atTime(10, 0), "Maria Silva", "(11) 99999-9999", "maria@email.com", PROFISSIONAL_ID);
    }

    private CreateAtendimentoResponseDTO respostaCriada(UUID id, LocalDate data) {
        return new CreateAtendimentoResponseDTO(id, UUID.randomUUID(), data.atTime(10, 0),
                LocalDate.now(), 45, StatusAtendimento.AGENDADO, List.<ServicoRealizadoDTO>of(), PROFISSIONAL_ID,
                "Ana");
    }

    @Test
    void naoDeveAgendarParaClinicaInexistente() {
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.agendar(SLUG, requisicao(LocalDate.now().plusDays(1))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void naoDeveAgendarParaClinicaInativa() {
        Clinica inativa = clinica();
        inativa.setAtiva(false);
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.agendar(SLUG, requisicao(LocalDate.now().plusDays(1))))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Clínica não encontrada");
    }

    @Test
    void deveCriarClienteNovoComOrigemOnlineETelefoneNormalizado() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);

        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(clienteRepository.findByTelefoneAndClinica("5511999999999", CLINICA))
                .thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        var resposta = bookingService.agendar(SLUG, requisicao(data));

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(captor.capture());
        assertThat(captor.getValue().getOrigem()).isEqualTo(OrigemCliente.ONLINE);
        assertThat(captor.getValue().getNome()).isEqualTo("Maria Silva");
        assertThat(captor.getValue().getTelefone()).isEqualTo("5511999999999");
        assertThat(captor.getValue().getEmail()).isEqualTo("maria@email.com");

        assertThat(resposta.cliente()).isEqualTo("Maria Silva");
        assertThat(resposta.servico()).isEqualTo("Limpeza de Pele");
        assertThat(resposta.duracaoMinutos()).isEqualTo(45);
    }

    @Test
    void deveConverterEmailEmBrancoParaNulo() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);

        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(clienteRepository.findByTelefoneAndClinica(any(), any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        bookingService.agendar(SLUG, new BookingRequestDTO(servico.getId(), data.atTime(10, 0),
                "Maria Silva", "11999999999", "   ", PROFISSIONAL_ID));

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isNull();
    }

    @Test
    void deveReutilizarClienteJaCadastrado() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", UUID.randomUUID());
        cliente.setNome("Maria Silva");

        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(clienteRepository.findByTelefoneAndClinica("5511999999999", CLINICA))
                .thenReturn(Optional.of(cliente));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        bookingService.agendar(SLUG, requisicao(data));

        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void deveDelegarValidaaoDeAgendaParaCreateAtendimento() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", UUID.randomUUID());
        cliente.setNome("Maria Silva");

        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(clienteRepository.findByTelefoneAndClinica(any(), any())).thenReturn(Optional.of(cliente));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        bookingService.agendar(SLUG, requisicao(data));

        // A validação (janela, expediente, lock e conflito) acontece dentro de
        // createAtendimento — não pode haver check fora do lock.
        verify(agendaService, never()).validarDisponibilidade(any(), any(), any(), any(Integer.class), any());
        verify(atendimentoService).createAtendimento(any(), any(CreateAtendimentoRequestDTO.class));
    }

    @Test
    void deveListarServicosDaClinica() {
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByClinica(CLINICA)).thenReturn(List.of(servico()));

        var servicos = bookingService.listarServicos(SLUG);

        assertThat(servicos).hasSize(1);
        assertThat(servicos.get(0).clinica()).isEqualTo("Clínica A");
        assertThat(servicos.get(0).duracaoMinutos()).isEqualTo(45);
    }

    @Test
    void telefoneNormalizadoDeveAdicionarDdi() {
        assertThat(BookingService.telefoneNormalizado("(11) 99999-9999")).isEqualTo("5511999999999");
        assertThat(BookingService.telefoneNormalizado("5511999999999")).isEqualTo("5511999999999");
    }

    @Test
    void agendamentoComProfissionalEscolhidoUsaEle() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(clienteRepository.findByTelefoneAndClinica(any(), any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        var resposta = bookingService.agendar(SLUG, requisicao(data));

        ArgumentCaptor<CreateAtendimentoRequestDTO> captor = ArgumentCaptor.forClass(CreateAtendimentoRequestDTO.class);
        verify(atendimentoService).createAtendimento(any(), captor.capture());
        assertThat(captor.getValue().profissionalId()).isEqualTo(PROFISSIONAL_ID);
        assertThat(resposta.profissional()).isEqualTo("Ana");
        verify(agendaService, never()).escolherLivre(any(), any(), any(), any(Integer.class));
    }

    @Test
    void semPreferenciaOSistemaEscolheQuemEstaLivre() {
        Servico servico = servico();
        LocalDate data = LocalDate.now().plusDays(1);
        Profissional bia = new Profissional();
        UUID biaId = UUID.randomUUID();
        ReflectionTestUtils.setField(bia, "id", biaId);
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(agendaService.escolherLivre(eq(CLINICA), eq(List.of(servico)), any(), eq(45))).thenReturn(bia);
        when(clienteRepository.findByTelefoneAndClinica(any(), any())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(atendimentoService.createAtendimento(any(), any(CreateAtendimentoRequestDTO.class)))
                .thenAnswer(i -> respostaCriada(UUID.randomUUID(), data));

        bookingService.agendar(SLUG, new BookingRequestDTO(servico.getId(), data.atTime(10, 0),
                "Maria Silva", "11999999999", null, null));

        ArgumentCaptor<CreateAtendimentoRequestDTO> captor = ArgumentCaptor.forClass(CreateAtendimentoRequestDTO.class);
        verify(atendimentoService).createAtendimento(any(), captor.capture());
        assertThat(captor.getValue().profissionalId()).isEqualTo(biaId);
    }

    @Test
    void listaPublicaDeProfissionaisMostraSoNomeEEspecialidade() {
        Servico servico = servico();
        Profissional ana = new Profissional();
        ReflectionTestUtils.setField(ana, "id", PROFISSIONAL_ID);
        ana.setNome("Ana");
        com.cliniva.tenancy.Especialidade fisio = new com.cliniva.tenancy.Especialidade();
        fisio.setNome("Fisioterapia");
        ana.getEspecialidades().add(fisio);
        when(clinicaRepository.findBySlugAndAtivaTrue(SLUG)).thenReturn(Optional.of(CLINICA));
        when(servicoRepository.findByIdAndClinica(any(), any())).thenReturn(Optional.of(servico));
        when(profissionalService.aptos(CLINICA, List.of(servico))).thenReturn(List.of(ana));

        var profissionais = bookingService.listarProfissionais(SLUG, servico.getId());

        assertThat(profissionais).singleElement().satisfies(p -> {
            assertThat(p.nome()).isEqualTo("Ana");
            assertThat(p.especialidades()).containsExactly("Fisioterapia");
        });
    }
}
