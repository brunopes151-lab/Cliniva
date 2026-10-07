package com.cliniva.atendimento.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliniva.atendimento.model.SerieAgendamento;

public interface SerieAgendamentoRepository extends JpaRepository<SerieAgendamento, UUID> {
}
