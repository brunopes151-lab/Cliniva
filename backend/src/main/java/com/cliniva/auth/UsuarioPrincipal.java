package com.cliniva.auth;

import java.util.UUID;

import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.Papel;

public record UsuarioPrincipal(
        UUID id,
        String supabaseUserId,
        Papel papel,
        Clinica clinica,
        String nome,
        String email,
        UUID profissionalId) {

    public boolean ehAdmin() {
        return papel == Papel.ADMIN;
    }

    public boolean ehProfissional() {
        return papel == Papel.PROFISSIONAL;
    }

    /** Nome para registros e trilhas; quem não preencheu o nome aparece pelo e-mail. */
    public String nomeExibicao() {
        return nome == null || nome.isBlank() ? email : nome;
    }
}
