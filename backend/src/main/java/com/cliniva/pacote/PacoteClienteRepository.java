package com.cliniva.pacote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacoteClienteRepository extends JpaRepository<PacoteCliente, UUID> {

    List<PacoteCliente> findByClinica_IdAndCliente_IdOrderByDataCompraDesc(UUID clinicaId, UUID clienteId);

    Optional<PacoteCliente> findByIdAndClinica_Id(UUID id, UUID clinicaId);

    /** Candidatos à baixa, do que vence primeiro para o que vence depois. */
    List<PacoteCliente> findByClinica_IdAndCliente_IdAndServico_IdAndStatusOrderByDataValidadeAscDataCompraAsc(
            UUID clinicaId, UUID clienteId, UUID servicoId, StatusPacoteCliente status);

    boolean existsByCliente_Id(UUID clienteId);

    /**
     * Baixa atômica: dois atendimentos concluídos ao mesmo tempo não leem o
     * mesmo saldo. Devolve 0 quando não havia saldo.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE PacoteCliente p SET p.saldo = p.saldo - 1 WHERE p.id = :id AND p.saldo > 0")
    int debitarSessao(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PacoteCliente p SET p.saldo = p.saldo + 1 WHERE p.id = :id AND p.saldo < p.sessoesTotal")
    int devolverSessao(@Param("id") UUID id);

    /**
     * Faturamento das vendas de pacote: entra na data da compra. Pacote
     * cancelado não conta.
     */
    @Query("SELECT COALESCE(SUM(p.valorPago), 0) FROM PacoteCliente p "
            + "WHERE p.clinica.id = :clinicaId AND p.status <> com.cliniva.pacote.StatusPacoteCliente.CANCELADO "
            + "AND p.dataCompra >= :inicio AND p.dataCompra <= :fim")
    BigDecimal somarVendas(@Param("clinicaId") UUID clinicaId, @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT p.clinica.id, COALESCE(SUM(p.valorPago), 0) FROM PacoteCliente p "
            + "WHERE p.status <> com.cliniva.pacote.StatusPacoteCliente.CANCELADO GROUP BY p.clinica.id")
    List<Object[]> totalVendidoPorClinica();

    @Query("SELECT COALESCE(SUM(p.valorPago), 0) FROM PacoteCliente p "
            + "WHERE p.cliente.id = :clienteId AND p.status <> com.cliniva.pacote.StatusPacoteCliente.CANCELADO")
    BigDecimal somarCompradoPeloCliente(@Param("clienteId") UUID clienteId);
}
