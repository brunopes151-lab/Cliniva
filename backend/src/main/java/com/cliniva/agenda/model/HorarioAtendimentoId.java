package com.cliniva.agenda.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class HorarioAtendimentoId implements Serializable {

    @Column(name = "clinica_id", nullable = false)
    private UUID clinicaId;

    /**
     * Quem atende. Entra na chave desde a migration 08 — é o que permite a
     * Ana trabalhar seg–qua e a Beatriz sex–dom no mesmo dia.
     */
    @Column(name = "profissional_id", nullable = false)
    private UUID profissionalId;

    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    protected HorarioAtendimentoId() {
    }

    public HorarioAtendimentoId(UUID clinicaId, UUID profissionalId, Integer diaSemana) {
        this.clinicaId = clinicaId;
        this.profissionalId = profissionalId;
        this.diaSemana = diaSemana;
    }

    public UUID getClinicaId() {
        return clinicaId;
    }

    public UUID getProfissionalId() {
        return profissionalId;
    }

    public Integer getDiaSemana() {
        return diaSemana;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HorarioAtendimentoId that)) {
            return false;
        }
        return java.util.Objects.equals(clinicaId, that.clinicaId)
                && java.util.Objects.equals(profissionalId, that.profissionalId)
                && java.util.Objects.equals(diaSemana, that.diaSemana);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(clinicaId, profissionalId, diaSemana);
    }
}