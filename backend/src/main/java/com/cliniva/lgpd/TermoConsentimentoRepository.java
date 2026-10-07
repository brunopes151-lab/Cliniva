package com.cliniva.lgpd;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermoConsentimentoRepository extends JpaRepository<TermoConsentimento, UUID> {

    Optional<TermoConsentimento> findFirstByClinica_IdOrderByVersaoDesc(UUID clinicaId);

    List<TermoConsentimento> findByClinica_IdOrderByVersaoDesc(UUID clinicaId);
}
