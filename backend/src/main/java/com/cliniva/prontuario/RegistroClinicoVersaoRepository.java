package com.cliniva.prontuario;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroClinicoVersaoRepository extends JpaRepository<RegistroClinicoVersao, UUID> {

    List<RegistroClinicoVersao> findByRegistro_IdOrderByNumeroDesc(UUID registroId);

    List<RegistroClinicoVersao> findByRegistro_IdInOrderByNumeroDesc(Collection<UUID> registroIds);
}
