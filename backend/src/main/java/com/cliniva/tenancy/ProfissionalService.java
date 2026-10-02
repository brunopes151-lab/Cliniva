package com.cliniva.tenancy;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Profissionais que atendem na clínica.
 *
 * <p>Por enquanto só o que o onboarding precisa: garantir o profissional
 * "Geral" e expor a listagem. O CRUD completo (criar, editar, ativar,
 * vincular serviço) é o bloco seguinte.
 */
@Service
@RequiredArgsConstructor
public class ProfissionalService {

    /** Nome do profissional fallback criado junto com a clínica. */
    public static final String NOME_GERAL = "Geral";

    private final ProfissionalRepository profissionalRepository;
    private final Clock clock;

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
        return profissionalRepository.findById(id)
                .filter(p -> p.getClinica().getId().equals(clinica.getId()))
                .orElseThrow(() -> new com.cliniva.exception.RecursoNaoEncontradoException(
                        "Profissional não encontrado"));
    }
}
