package com.cliniva.tenancy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.cliniva.servico.Servico;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
 * <p>O profissional "Geral" existe em toda clínica: guarda o expediente
 * padrão (modelo para os profissionais novos) e os atendimentos anteriores
 * à dimensão profissional. Pode ser desativado quando a clínica cadastrar
 * a equipe de verdade.
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

    @ManyToMany
    @JoinTable(name = "profissional_especialidade",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id"))
    private Set<Especialidade> especialidades = new HashSet<>();

    /**
     * Serviços que este profissional executa. Um serviço sem nenhum
     * profissional vinculado pode ser feito por qualquer profissional ativo.
     */
    @ManyToMany
    @JoinTable(name = "servico_profissional",
            joinColumns = @JoinColumn(name = "profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "servico_id"))
    private Set<Servico> servicos = new HashSet<>();

    public boolean ehGeral() {
        return ProfissionalService.NOME_GERAL.equalsIgnoreCase(nome);
    }
}
