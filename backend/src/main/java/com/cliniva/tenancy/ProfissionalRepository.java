package com.cliniva.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    Optional<Profissional> findByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    Optional<Profissional> findFirstByClinica_IdAndAtivoTrueOrderByNomeAsc(UUID clinicaId);

    List<Profissional> findByClinica_IdOrderByNomeAsc(UUID clinicaId);

    boolean existsByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    boolean existsByClinica_IdAndNomeIgnoreCaseAndIdNot(UUID clinicaId, String nome, UUID id);
}
