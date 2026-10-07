package com.cliniva.atendimento.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.cliniva.atendimento.enums.FrequenciaSerie;
import com.cliniva.cliente.Cliente;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.Profissional;

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
 * Regra com que uma série de atendimentos foi gerada. Cada sessão é um
 * {@link Atendimento} comum apontando para cá; remarcar ou cancelar uma
 * sessão não muda a regra.
 */
@Entity(name = "SerieAgendamento")
@Table(name = "serie_agendamento")
@Getter
@Setter
@NoArgsConstructor
public class SerieAgendamento {

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
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequencia", nullable = false, length = 20)
    private FrequenciaSerie frequencia;

    @Column(name = "inclui_sabado", nullable = false)
    private boolean incluiSabado;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "quantidade")
    private Integer quantidade;
}
