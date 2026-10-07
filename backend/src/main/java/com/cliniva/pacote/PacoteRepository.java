package com.cliniva.pacote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacoteRepository extends JpaRepository<Pacote, UUID> {

    List<Pacote> findByClinica_IdOrderByNomeAsc(UUID clinicaId);

    Optional<Pacote> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    boolean existsByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    boolean existsByClinica_IdAndNomeIgnoreCaseAndIdNot(UUID clinicaId, String nome, UUID id);
}
