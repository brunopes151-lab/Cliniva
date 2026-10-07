package com.cliniva.pacote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cliniva.cliente.Cliente;
import com.cliniva.servico.Servico;
import com.cliniva.tenancy.Clinica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pacote comprado por um paciente. Nome, serviço e nº de sessões são
 * copiados do modelo na venda, para que mudar o modelo depois não altere o
 * que o paciente já comprou.
 */
@Entity(name = "PacoteCliente")
@Table(name = "pacote_cliente")
@Getter
@Setter
@NoArgsConstructor
public class PacoteCliente {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pacote_id", nullable = false)
    private Pacote pacote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "sessoes_total", nullable = false)
    private Integer sessoesTotal;

    @Column(name = "saldo", nullable = false)
    private Integer saldo;

    @Column(name = "data_compra", nullable = false)
    private LocalDate dataCompra;

    @Column(name = "data_validade", nullable = false)
    private LocalDate dataValidade;

    @Column(name = "valor_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusPacoteCliente status = StatusPacoteCliente.ATIVO;

    /** Vale para uma sessão no dia {@code data}: ativo, com saldo e dentro da validade. */
    public boolean usavelEm(LocalDate data) {
        return status == StatusPacoteCliente.ATIVO && saldo > 0 && !data.isAfter(dataValidade);
    }

    public SituacaoPacote situacao(LocalDate hoje) {
        if (status == StatusPacoteCliente.CANCELADO) {
            return SituacaoPacote.CANCELADO;
        }
        if (saldo <= 0) {
            return SituacaoPacote.ESGOTADO;
        }
        if (hoje.isAfter(dataValidade)) {
            return SituacaoPacote.VENCIDO;
        }
        return SituacaoPacote.ATIVO;
    }
}
