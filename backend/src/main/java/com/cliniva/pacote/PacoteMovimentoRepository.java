package com.cliniva.pacote;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacoteMovimentoRepository extends JpaRepository<PacoteMovimento, UUID> {

    List<PacoteMovimento> findByAtendimento_IdOrderByCriadoEmAsc(UUID atendimentoId);

    List<PacoteMovimento> findByPacoteCliente_IdOrderByCriadoEmDesc(UUID pacoteClienteId);
}
