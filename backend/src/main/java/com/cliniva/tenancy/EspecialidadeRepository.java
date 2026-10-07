package com.cliniva.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EspecialidadeRepository extends JpaRepository<Especialidade, UUID> {

    List<Especialidade> findByClinica_IdOrderByNomeAsc(UUID clinicaId);

    Optional<Especialidade> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    boolean existsByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    boolean existsByClinica_IdAndNomeIgnoreCaseAndIdNot(UUID clinicaId, String nome, UUID id);
}
