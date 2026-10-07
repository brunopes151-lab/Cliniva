package com.cliniva.pacote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.model.AtendimentoServico;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.atendimento.repository.AtendimentoServicoRepository;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.TransicaoStatusInvalidaException;
import com.cliniva.pacote.dtos.PacoteDtos.VenderPacoteRequestDTO;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PacoteServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 7);
    private static final Clock CLOCK = Clock.fixed(HOJE.atTime(9, 0).atZone(ZoneId.of("America/Sao_Paulo"))
            .toInstant(), ZoneId.of("America/Sao_Paulo"));

    @Mock
    private PacoteRepository pacoteRepository;
    @Mock
    private PacoteClienteRepository pacoteClienteRepository;
    @Mock
    private PacoteMovimentoRepository movimentoRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AtendimentoRepository atendimentoRepository;
    @Mock
    private AtendimentoServicoRepository atendimentoServicoRepository;
    @Mock
    private ClinicaContext clinicaContext;

    private PacoteService service;
    private Clinica clinica;
    private Cliente paciente;
    private Servico fisio;
    private Pacote modelo;

    @BeforeEach
    void setUp() {
        service = new PacoteService(pacoteRepository, pacoteClienteRepository, movimentoRepository,
                servicoRepository, clienteRepository, atendimentoRepository, atendimentoServicoRepository,
                clinicaContext, CLOCK);
        clinica = new Clinica();
        clinica.setId(UUID.randomUUID());
        paciente = new Cliente();
        paciente.setId(UUID.randomUUID());
        fisio = new Servico();
        fisio.setId(UUID.randomUUID());
        fisio.setNome("Fisioterapia");
        modelo = new Pacote();
        modelo.setId(UUID.randomUUID());
        modelo.setNome("10 sessões de fisio");
        modelo.setServico(fisio);
        modelo.setSessoes(10);
        modelo.setValidadeDias(90);
        modelo.setPreco(new BigDecimal("900.00"));
        when(clinicaContext.profissionalRestrito()).thenReturn(Optional.empty());
        when(clienteRepository.findByIdAndClinica(paciente.getId(), clinica)).thenReturn(Optional.of(paciente));
        when(pacoteRepository.findByIdAndClinica_Id(modelo.getId(), clinica.getId())).thenReturn(Optional.of(modelo));
        when(pacoteClienteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pacoteClienteRepository.debitarSessao(any())).thenReturn(1);
        when(pacoteClienteRepository.devolverSessao(any())).thenReturn(1);
    }

    @Test
    void vendaCopiaOModeloComSaldoCheioEValidadeContadaDaCompra() {
        var vendido = service.vender(clinica, paciente.getId(), new VenderPacoteRequestDTO(modelo.getId(), null, null));

        assertThat(vendido.saldo()).isEqualTo(10);
        assertThat(vendido.sessoesTotal()).isEqualTo(10);
        assertThat(vendido.dataCompra()).isEqualTo(HOJE);
        assertThat(vendido.dataValidade()).isEqualTo(HOJE.plusDays(90));
        assertThat(vendido.valorPago()).isEqualByComparingTo("900.00");
        assertThat(vendido.situacao()).isEqualTo(SituacaoPacote.ATIVO);
    }

    @Test
    void vendaComDescontoEDataInformada() {
        var vendido = service.vender(clinica, paciente.getId(),
                new VenderPacoteRequestDTO(modelo.getId(), HOJE.minusDays(5), new BigDecimal("800.00")));

        assertThat(vendido.dataValidade()).isEqualTo(HOJE.plusDays(85));
        assertThat(vendido.valorPago()).isEqualByComparingTo("800.00");
    }

    @Test
    void naoVendeModeloInativo() {
        modelo.setAtivo(false);
        assertThatThrownBy(() -> service.vender(clinica, paciente.getId(),
                new VenderPacoteRequestDTO(modelo.getId(), null, null)))
                .isInstanceOf(TransicaoStatusInvalidaException.class);
    }

    @Test
    void profissionalNaoVePacoteDePacienteQueNaoAtende() {
        UUID ana = UUID.randomUUID();
        when(clinicaContext.profissionalRestrito()).thenReturn(Optional.of(ana));
        when(atendimentoRepository.existsByCliente_IdAndProfissional_Id(paciente.getId(), ana)).thenReturn(false);

        assertThatThrownBy(() -> service.listarDoCliente(clinica, paciente.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void conclusaoBaixaDoPacoteQueVencePrimeiro() {
        PacoteCliente venceAntes = pacote(5, HOJE.plusDays(10));
        PacoteCliente venceDepois = pacote(5, HOJE.plusDays(60));
        Atendimento atendimento = atendimentoDe(fisio, HOJE.atTime(10, 0));
        candidatos(venceAntes, venceDepois);

        service.baixarSessoes(atendimento);

        verify(pacoteClienteRepository).debitarSessao(venceAntes.getId());
        verify(pacoteClienteRepository, never()).debitarSessao(venceDepois.getId());
        ArgumentCaptor<PacoteMovimento> movimento = ArgumentCaptor.forClass(PacoteMovimento.class);
        verify(movimentoRepository).save(movimento.capture());
        assertThat(movimento.getValue().getTipo()).isEqualTo(TipoMovimentoPacote.BAIXA);
        assertThat(movimento.getValue().getAtendimento()).isSameAs(atendimento);
    }

    @Test
    void pacoteVencidoNaDataDoAtendimentoNaoDaBaixa() {
        PacoteCliente vencido = pacote(5, HOJE.minusDays(1));
        candidatos(vencido);

        service.baixarSessoes(atendimentoDe(fisio, HOJE.atTime(10, 0)));

        verify(pacoteClienteRepository, never()).debitarSessao(any());
        verify(movimentoRepository, never()).save(any());
    }

    @Test
    void pacoteValidoAteODiaDoAtendimentoAindaDaBaixa() {
        PacoteCliente venceHoje = pacote(1, HOJE);
        candidatos(venceHoje);

        service.baixarSessoes(atendimentoDe(fisio, HOJE.atTime(17, 0)));

        verify(pacoteClienteRepository).debitarSessao(venceHoje.getId());
    }

    @Test
    void semSaldoPassaParaOProximoOuNaoBaixa() {
        PacoteCliente esgotado = pacote(0, HOJE.plusDays(5));
        PacoteCliente comSaldo = pacote(2, HOJE.plusDays(30));
        candidatos(esgotado, comSaldo);

        service.baixarSessoes(atendimentoDe(fisio, HOJE.atTime(10, 0)));

        verify(pacoteClienteRepository, never()).debitarSessao(esgotado.getId());
        verify(pacoteClienteRepository).debitarSessao(comSaldo.getId());
    }

    @Test
    void corridaPeloUltimoSaldoNaoGravaMovimento() {
        PacoteCliente ultimo = pacote(1, HOJE.plusDays(5));
        candidatos(ultimo);
        when(pacoteClienteRepository.debitarSessao(ultimo.getId())).thenReturn(0);

        service.baixarSessoes(atendimentoDe(fisio, HOJE.atTime(10, 0)));

        verify(movimentoRepository, never()).save(any());
    }

    @Test
    void semPacoteNadaAcontece() {
        candidatos();
        service.baixarSessoes(atendimentoDe(fisio, HOJE.atTime(10, 0)));
        verify(movimentoRepository, never()).save(any());
    }

    @Test
    void estornoDevolveSoOQueAindaNaoFoiDevolvido() {
        PacoteCliente pc = pacote(4, HOJE.plusDays(30));
        Atendimento atendimento = atendimentoDe(fisio, HOJE.atTime(10, 0));
        // Concluído, voltou para agendado (estorno), concluído de novo: falta devolver 1.
        when(movimentoRepository.findByAtendimento_IdOrderByCriadoEmAsc(atendimento.getId())).thenReturn(List.of(
                movimento(pc, TipoMovimentoPacote.BAIXA), movimento(pc, TipoMovimentoPacote.ESTORNO),
                movimento(pc, TipoMovimentoPacote.BAIXA)));

        service.estornarSessoes(atendimento);

        verify(pacoteClienteRepository, times(1)).devolverSessao(pc.getId());
        ArgumentCaptor<PacoteMovimento> salvo = ArgumentCaptor.forClass(PacoteMovimento.class);
        verify(movimentoRepository).save(salvo.capture());
        assertThat(salvo.getValue().getTipo()).isEqualTo(TipoMovimentoPacote.ESTORNO);
    }

    @Test
    void estornoSemBaixaNaoMexeNoSaldo() {
        Atendimento atendimento = atendimentoDe(fisio, HOJE.atTime(10, 0));
        when(movimentoRepository.findByAtendimento_IdOrderByCriadoEmAsc(atendimento.getId())).thenReturn(List.of());

        service.estornarSessoes(atendimento);

        verify(pacoteClienteRepository, never()).devolverSessao(any());
    }

    @Test
    void situacaoSaiDoSaldoEDaValidade() {
        assertThat(pacote(3, HOJE).situacao(HOJE)).isEqualTo(SituacaoPacote.ATIVO);
        assertThat(pacote(3, HOJE.minusDays(1)).situacao(HOJE)).isEqualTo(SituacaoPacote.VENCIDO);
        assertThat(pacote(0, HOJE.plusDays(9)).situacao(HOJE)).isEqualTo(SituacaoPacote.ESGOTADO);
        PacoteCliente cancelado = pacote(3, HOJE.plusDays(9));
        cancelado.setStatus(StatusPacoteCliente.CANCELADO);
        assertThat(cancelado.situacao(HOJE)).isEqualTo(SituacaoPacote.CANCELADO);
    }

    @Test
    void cancelarPacoteDeOutroPacienteResponde404() {
        PacoteCliente deOutro = pacote(3, HOJE.plusDays(9));
        Cliente outro = new Cliente();
        outro.setId(UUID.randomUUID());
        deOutro.setCliente(outro);
        when(pacoteClienteRepository.findByIdAndClinica_Id(deOutro.getId(), clinica.getId()))
                .thenReturn(Optional.of(deOutro));

        assertThatThrownBy(() -> service.cancelar(clinica, paciente.getId(), deOutro.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private void candidatos(PacoteCliente... pacotes) {
        when(pacoteClienteRepository
                .findByClinica_IdAndCliente_IdAndServico_IdAndStatusOrderByDataValidadeAscDataCompraAsc(
                        clinica.getId(), paciente.getId(), fisio.getId(), StatusPacoteCliente.ATIVO))
                .thenReturn(List.of(pacotes));
    }

    private PacoteCliente pacote(int saldo, LocalDate validade) {
        PacoteCliente pc = new PacoteCliente();
        pc.setId(UUID.randomUUID());
        pc.setClinica(clinica);
        pc.setCliente(paciente);
        pc.setPacote(modelo);
        pc.setServico(fisio);
        pc.setNome(modelo.getNome());
        pc.setSessoesTotal(10);
        pc.setSaldo(saldo);
        pc.setDataCompra(validade.minusDays(90));
        pc.setDataValidade(validade);
        pc.setValorPago(BigDecimal.TEN);
        return pc;
    }

    private Atendimento atendimentoDe(Servico servico, LocalDateTime quando) {
        Atendimento a = new Atendimento();
        a.setId(UUID.randomUUID());
        a.setClinica(clinica);
        a.setCliente(paciente);
        a.setDataAtendimento(quando);
        AtendimentoServico as = new AtendimentoServico();
        as.setAtendimento(a);
        as.setServico(servico);
        when(atendimentoServicoRepository.findByAtendimento(a)).thenReturn(List.of(as));
        return a;
    }

    private PacoteMovimento movimento(PacoteCliente pc, TipoMovimentoPacote tipo) {
        PacoteMovimento m = new PacoteMovimento();
        m.setPacoteCliente(pc);
        m.setTipo(tipo);
        m.setCriadoEm(LocalDateTime.now(CLOCK));
        return m;
    }
}
