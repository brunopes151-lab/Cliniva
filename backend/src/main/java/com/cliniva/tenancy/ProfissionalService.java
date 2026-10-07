package com.cliniva.tenancy;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;

import lombok.RequiredArgsConstructor;

/**
 * Consultas e regras sobre quem atende. O cadastro (criar, editar,
 * especialidades, serviços) fica em {@code equipe.EquipeService}.
 */
@Service
@RequiredArgsConstructor
public class ProfissionalService {

    /** Nome do profissional fallback criado junto com a clínica. */
    public static final String NOME_GERAL = "Geral";

    private final ProfissionalRepository profissionalRepository;

    /**
     * Devolve o profissional "Geral" da clínica, criando se ainda não existir.
     *
     * <p>Toda clínica tem um: é o que segura o expediente padrão e o
     * histórico anterior à dimensão profissional. A clínica pode cadastrar
     * gente de verdade depois; o "Geral" continua existindo.
     */
    @Transactional
    public Profissional garantirGeral(Clinica clinica) {
        return profissionalRepository
                .findByClinica_IdAndNomeIgnoreCase(clinica.getId(), NOME_GERAL)
                .orElseGet(() -> criar(clinica, NOME_GERAL, null));
    }

    @Transactional
    public Profissional criar(Clinica clinica, String nome, String cor) {
        Profissional profissional = new Profissional();
        profissional.setClinica(clinica);
        profissional.setNome(nome.trim());
        profissional.setCor(cor);
        profissional.setAtivo(true);
        return profissionalRepository.save(profissional);
    }

    @Transactional(readOnly = true)
    public List<Profissional> listar(Clinica clinica) {
        return profissionalRepository.findByClinica_IdOrderByNomeAsc(clinica.getId());
    }

    @Transactional(readOnly = true)
    public Profissional buscar(Clinica clinica, UUID id) {
        return profissionalRepository.findByIdAndClinica_Id(id, clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrado"));
    }

    /** Profissionais ativos que executam TODOS os serviços informados, em ordem de nome. */
    @Transactional(readOnly = true)
    public List<Profissional> aptos(Clinica clinica, Collection<Servico> servicos) {
        return profissionalRepository.findByClinica_IdAndAtivoTrueOrderByNomeAsc(clinica.getId()).stream()
                .filter(profissional -> executaTodos(profissional, servicos))
                .toList();
    }

    /** Recusa (400) um profissional inativo ou que não faz algum dos serviços. */
    public void exigirApto(Profissional profissional, Collection<Servico> servicos) {
        if (!profissional.isAtivo()) {
            throw new IllegalArgumentException("Profissional " + profissional.getNome() + " está inativo");
        }
        for (Servico servico : servicos) {
            if (!executa(profissional, servico)) {
                throw new IllegalArgumentException(
                        profissional.getNome() + " não realiza o serviço " + servico.getNome());
            }
        }
    }

    private boolean executaTodos(Profissional profissional, Collection<Servico> servicos) {
        return servicos.stream().allMatch(servico -> executa(profissional, servico));
    }

    private boolean executa(Profissional profissional, Servico servico) {
        List<UUID> vinculados = profissionalRepository.idsVinculadosAoServico(servico.getId());
        return vinculados.isEmpty() || vinculados.contains(profissional.getId());
    }
}
