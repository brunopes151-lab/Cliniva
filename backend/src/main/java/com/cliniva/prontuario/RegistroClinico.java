package com.cliniva.prontuario;

import java.time.LocalDateTime;
import java.util.UUID;

import com.cliniva.atendimento.model.Atendimento;
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

/** Uma entrada do prontuário. O conteúdo fica nas versões; o banco recusa apagar. */
@Entity(name = "RegistroClinico")
@Table(name = "registro_clinico")
@Getter
@Setter
@NoArgsConstructor
public class RegistroClinico {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false, updatable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false, updatable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", updatable = false)
    private Atendimento atendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30, updatable = false)
    private TipoRegistro tipo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}
