package com.cliniva.pacote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.cliniva.atendimento.model.Atendimento;
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

/** Histórico do saldo de um pacote. O banco só aceita inserção. */
@Entity(name = "PacoteMovimento")
@Table(name = "pacote_movimento")
@Getter
@Setter
@NoArgsConstructor
public class PacoteMovimento {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pacote_cliente_id", nullable = false)
    private PacoteCliente pacoteCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id")
    private Atendimento atendimento;

    /** Serviço do atendimento que a baixa zerou (ou que o estorno devolveu). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    /** Valor que o serviço tinha antes da baixa; o estorno o devolve. */
    @Column(name = "valor_cobrado", precision = 10, scale = 2)
    private BigDecimal valorCobrado;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimentoPacote tipo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
