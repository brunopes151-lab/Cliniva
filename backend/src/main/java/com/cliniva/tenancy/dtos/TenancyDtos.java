package com.cliniva.tenancy.dtos;

import java.util.UUID;

import com.cliniva.tenancy.Papel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TenancyDtos {

    private TenancyDtos() {
    }

    public record CadastroOnboardingRequestDTO(
            @NotBlank(message = "Nome da clínica é obrigatório") String nomeClinica,
            @NotBlank(message = "Nome do responsável é obrigatório") String nomeResponsavel,
            @NotBlank(message = "E-mail é obrigatório") @Email(message = "E-mail inválido") String email) {
    }

    public record CadastroOnboardingResponseDTO(UUID clinicaId, String clinicaNome, UUID responsavelId) {
    }

    /** Nome e logo exibidos nas telas. logoDataUrl null = símbolo neutro. */
    public record MarcaResponseDTO(String nome, String logoDataUrl) {
    }

    public record AtualizarMarcaRequestDTO(
            @NotBlank(message = "Nome da clínica é obrigatório")
            @Size(max = 120, message = "Nome da clínica deve ter no máximo 120 caracteres") String nome,
            @Size(max = 400000, message = "Logo muito grande (máximo ~300 KB)")
            @Pattern(regexp = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
                    message = "Logo deve ser uma imagem PNG, JPEG ou WebP") String logoDataUrl) {
    }

    public record MeResponseDTO(UUID id, String nome, String email, Papel papel, UUID clinicaId,
            String clinicaNome) {
    }
}