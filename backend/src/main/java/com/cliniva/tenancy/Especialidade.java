package com.cliniva.tenancy;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Área de atuação de um profissional (Estética, Fisioterapia...). Nome único por clínica. */
@Entity(name = "Especialidade")
@Table(name = "especialidade")
@Getter
@Setter
@NoArgsConstructor
public class Especialidade {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;
}
