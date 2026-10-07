package com.cliniva.lgpd;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaAcessoRepository extends JpaRepository<AuditoriaAcesso, UUID> {

    List<AuditoriaAcesso> findByClinica_IdAndClienteIdOrderByCriadoEmDesc(UUID clinicaId, UUID clienteId);
}
