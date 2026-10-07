package com.cliniva.lgpd;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.cliniva.cliente.Cliente;
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

/**
 * Aceite de um paciente a uma versão do termo. Só os campos de revogação
 * mudam, uma vez; excluir o paciente leva o consentimento junto.
 */
@Entity(name = "ConsentimentoPaciente")
@Table(name = "consentimento_paciente")
@Getter
@Setter
@NoArgsConstructor
public class ConsentimentoPaciente {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "termo_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TermoConsentimento termo;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private LocalDateTime aceitoEm;

    @Column(name = "registrado_por_id", updatable = false)
    private UUID registradoPorId;

    @Column(name = "registrado_por_nome", nullable = false, length = 150, updatable = false)
    private String registradoPorNome;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    @Column(name = "revogado_por_nome", length = 150)
    private String revogadoPorNome;

    @Column(name = "motivo_revogacao", length = 500)
    private String motivoRevogacao;

    public boolean isVigente() {
        return revogadoEm == null;
    }
}
