package com.cliniva.prontuario;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.Usuario;

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

/** Conteúdo de um registro. Só se insere: corrigir é gravar o número seguinte. */
@Entity(name = "RegistroClinicoVersao")
@Table(name = "registro_clinico_versao")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class RegistroClinicoVersao {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_id", nullable = false)
    private RegistroClinico registro;

    @Column(name = "numero", nullable = false)
    private int numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modelo_ficha_id", nullable = false)
    private ModeloFicha modelo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conteudo", nullable = false)
    private Map<String, Object> conteudo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "autor_nome", nullable = false, length = 150)
    private String autorNome;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
