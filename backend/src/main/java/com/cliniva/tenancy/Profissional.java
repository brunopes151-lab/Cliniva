package com.cliniva.tenancy;

import java.util.List;
import java.util.Optional;
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

/**
 * Pessoa que atende na clínica.
 *
 * <p>Entidade de apresentação, separada de {@link Usuario} (que é acesso).
 * A separação é deliberada: um profissional pode não ter login nenhum — a
 * clínica cadastra a Ana, a Ana nunca entra no sistema — e um usuário com
 * papel OWNER/ADMIN não precisa ser profissional. O vínculo é opcional e
 * fica em {@code usuario.profissional_id}.
 *
 * <p>Nome único por clínica ({@code uk_profissional_nome}). Apagar quem tem
 * histórico é bloqueado pelo banco ({@code ON DELETE RESTRICT}).
 */
@Entity(name = "Profissional")
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
public class Profissional {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /** Cor hexadecimal (#RRGGBB) para pintar a grade. Null = o front deriva do nome. */
    @Column(name = "cor", length = 7)
    private String cor;

    /** Inativo não aparece em agendamento novo, mas preserva o histórico. */
    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;
}
