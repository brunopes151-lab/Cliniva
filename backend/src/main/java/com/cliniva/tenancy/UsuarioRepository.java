package com.cliniva.tenancy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    @EntityGraph(attributePaths = { "clinica", "profissional" })
    Optional<Usuario> findBySupabaseUserId(String supabaseUserId);

    @EntityGraph(attributePaths = { "clinica", "profissional" })
    Optional<Usuario> findByEmailIgnoreCase(String email);

    default Optional<Usuario> findByEmail(String email) {
        return findByEmailIgnoreCase(email);
    }

    List<Usuario> findByClinica_IdOrderByNomeAsc(UUID clinicaId);

    Optional<Usuario> findByProfissional_Id(UUID profissionalId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}