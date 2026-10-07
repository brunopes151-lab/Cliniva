package com.cliniva.lgpd;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.Papel;

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
 * Uma linha por acesso a dados clínicos. Só se insere. Paciente e usuário
 * ficam sem chave estrangeira para o rastro sobreviver ao cadastro.
 */
@Entity(name = "AuditoriaAcesso")
@Table(name = "auditoria_acesso")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaAcesso {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Clinica clinica;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(name = "registro_id")
    private UUID registroId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", nullable = false, length = 150)
    private String usuarioNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private Papel papel;

    @Column(name = "modo_suporte", nullable = false)
    private boolean modoSuporte;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false, length = 40)
    private AcaoAuditoria acao;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
