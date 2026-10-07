package com.cliniva.lgpd;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

/** Texto do termo de consentimento. Mudar o texto publica a versão seguinte. */
@Entity(name = "TermoConsentimento")
@Table(name = "termo_consentimento")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class TermoConsentimento {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clinica clinica;

    @Column(name = "versao", nullable = false)
    private int versao;

    @Column(name = "texto", nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "publicado_por", length = 150)
    private String publicadoPor;
}
