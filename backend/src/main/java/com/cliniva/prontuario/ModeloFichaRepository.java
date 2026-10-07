package com.cliniva.prontuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloFichaRepository extends JpaRepository<ModeloFicha, UUID> {

    List<ModeloFicha> findByClinica_IdOrderByVersaoDesc(UUID clinicaId);

    List<ModeloFicha> findByClinica_IdAndFamiliaIdOrderByVersaoDesc(UUID clinicaId, UUID familiaId);

    Optional<ModeloFicha> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    boolean existsByClinica_Id(UUID clinicaId);

    boolean existsByClinica_IdAndNomeIgnoreCaseAndFamiliaIdNot(UUID clinicaId, String nome, UUID familiaId);
}
