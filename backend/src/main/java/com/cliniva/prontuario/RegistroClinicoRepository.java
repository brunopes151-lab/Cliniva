package com.cliniva.prontuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroClinicoRepository extends JpaRepository<RegistroClinico, UUID> {

    List<RegistroClinico> findByClinica_IdAndCliente_IdOrderByCriadoEmDesc(UUID clinicaId, UUID clienteId);

    Optional<RegistroClinico> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    boolean existsByCliente_Id(UUID clienteId);

    boolean existsByCliente_IdAndProfissional_Id(UUID clienteId, UUID profissionalId);
}
