package com.cliniva.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    Optional<Profissional> findByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    Optional<Profissional> findFirstByClinica_IdAndAtivoTrueOrderByNomeAsc(UUID clinicaId);

    Optional<Profissional> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    List<Profissional> findByClinica_IdOrderByNomeAsc(UUID clinicaId);

    List<Profissional> findByClinica_IdAndAtivoTrueOrderByNomeAsc(UUID clinicaId);

    boolean existsByClinica_IdAndNomeIgnoreCase(UUID clinicaId, String nome);

    boolean existsByClinica_IdAndNomeIgnoreCaseAndIdNot(UUID clinicaId, String nome, UUID id);

    /** Ids dos profissionais vinculados ao serviço. Vazio = qualquer um pode fazer. */
    @Query("SELECT p.id FROM Profissional p JOIN p.servicos s WHERE s.id = :servicoId")
    List<UUID> idsVinculadosAoServico(@Param("servicoId") UUID servicoId);
}
