package com.cliniva.lgpd;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentimentoPacienteRepository extends JpaRepository<ConsentimentoPaciente, UUID> {

    List<ConsentimentoPaciente> findByClinica_IdAndCliente_IdOrderByAceitoEmDesc(UUID clinicaId, UUID clienteId);

    Optional<ConsentimentoPaciente> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    boolean existsByCliente_IdAndRevogadoEmIsNull(UUID clienteId);
}
