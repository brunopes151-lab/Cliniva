package com.cliniva.prontuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Formulário do prontuário. Editar cria outra linha da mesma família com
 * {@code versao} + 1; os registros apontam para a versão exata que usaram.
 */
@Entity(name = "ModeloFicha")
@Table(name = "modelo_ficha")
@Getter
@Setter
@NoArgsConstructor
public class ModeloFicha {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @Column(name = "versao", nullable = false)
    private int versao;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", nullable = false, length = 20)
    private AreaFicha area;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoRegistro tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campos", nullable = false)
    private List<CampoFicha> campos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
