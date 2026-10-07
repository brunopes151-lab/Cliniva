package com.cliniva.pacote;

import java.math.BigDecimal;
import java.util.UUID;

import com.cliniva.servico.Servico;
import com.cliniva.tenancy.Clinica;

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

/** Modelo de pacote que a clínica vende: N sessões de um serviço, com validade e preço. */
@Entity(name = "Pacote")
@Table(name = "pacote")
@Getter
@Setter
@NoArgsConstructor
public class Pacote {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "sessoes", nullable = false)
    private Integer sessoes;

    @Column(name = "validade_dias", nullable = false)
    private Integer validadeDias;

    @Column(name = "preco", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;
}
