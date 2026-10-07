package com.cliniva.agenda.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cliniva.agenda.model.HorarioAtendimento;
import com.cliniva.agenda.model.HorarioAtendimentoId;
import com.cliniva.tenancy.Clinica;

public interface HorarioAtendimentoRepository
        extends JpaRepository<HorarioAtendimento, HorarioAtendimentoId> {

    List<HorarioAtendimento> findByClinicaOrderByIdDiaSemanaAsc(Clinica clinica);

    List<HorarioAtendimento> findByClinicaAndIdProfissionalIdOrderByIdDiaSemanaAsc(Clinica clinica,
            UUID profissionalId);
}
