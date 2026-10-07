package com.cliniva.tenancy.dtos;

import java.util.List;
import java.util.UUID;

import com.cliniva.tenancy.Papel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Profissionais, especialidades e usuários da clínica. */
public final class EquipeDtos {

    private EquipeDtos() {
    }

    public record EspecialidadeDTO(UUID id, String nome) {
    }

    public record SalvarEspecialidadeRequestDTO(
            @NotBlank(message = "Nome da especialidade é obrigatório")
            @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres") String nome) {
    }

    /**
     * @param geral     true para o profissional "Geral" (expediente padrão da clínica)
     * @param servicoIds vazio = executa qualquer serviço que não tenha vínculo
     */
    public record ProfissionalResponseDTO(UUID id, String nome, String cor, boolean ativo, boolean geral,
            List<EspecialidadeDTO> especialidades, List<UUID> servicoIds) {
    }

    public record SalvarProfissionalRequestDTO(
            @NotBlank(message = "Nome do profissional é obrigatório")
            @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB") String cor,
            Boolean ativo,
            List<UUID> especialidadeIds,
            List<UUID> servicoIds) {
    }

    /** O que o paciente vê no agendamento online: sem nada além do nome e da área. */
    public record ProfissionalPublicoDTO(UUID id, String nome, List<String> especialidades) {
    }

    public record UsuarioClinicaResponseDTO(UUID id, String nome, String email, Papel papel, boolean ativo,
            UUID profissionalId, String profissionalNome) {
    }

    public record CriarUsuarioClinicaRequestDTO(
            @NotBlank(message = "Nome é obrigatório") @Size(max = 120) String nome,
            @NotBlank(message = "E-mail é obrigatório") @Email(message = "E-mail inválido") String email,
            @NotNull(message = "Perfil é obrigatório") Papel papel,
            UUID profissionalId) {
    }

    public record AtualizarUsuarioClinicaRequestDTO(
            @NotBlank(message = "Nome é obrigatório") @Size(max = 120) String nome,
            @NotNull(message = "Perfil é obrigatório") Papel papel,
            UUID profissionalId,
            @NotNull(message = "Indique se o acesso está ativo") Boolean ativo) {
    }

    /** senhaTemporaria só vem preenchida quando o Supabase está configurado. */
    public record UsuarioCriadoResponseDTO(UsuarioClinicaResponseDTO usuario, String senhaTemporaria) {
    }

    public record SenhaTemporariaResponseDTO(String email, String senhaTemporaria) {
    }
}
