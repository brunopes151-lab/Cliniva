package com.cliniva.prontuario.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cliniva.prontuario.AreaFicha;
import com.cliniva.prontuario.CampoFicha;
import com.cliniva.prontuario.TipoRegistro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Modelos de ficha e prontuário do paciente. */
public final class ProntuarioDtos {

    private ProntuarioDtos() {
    }

    public record ModeloFichaDTO(UUID id, UUID familiaId, int versao, String nome, AreaFicha area,
            TipoRegistro tipo, List<CampoFicha> campos, boolean ativo) {
    }

    public record SalvarModeloRequestDTO(
            @NotBlank(message = "Nome do modelo é obrigatório")
            @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
            @NotNull(message = "Área é obrigatória") AreaFicha area,
            @NotNull(message = "Tipo de registro é obrigatório") TipoRegistro tipo,
            @NotEmpty(message = "O modelo precisa ter pelo menos uma pergunta")
            @Size(max = 60, message = "O modelo pode ter no máximo 60 perguntas") @Valid List<CampoFicha> campos) {
    }

    public record AtivoRequestDTO(boolean ativo) {
    }

    public record NovoRegistroRequestDTO(
            @NotNull(message = "Modelo é obrigatório") UUID modeloId,
            UUID atendimentoId,
            @NotNull(message = "Conteúdo é obrigatório") Map<String, Object> conteudo) {
    }

    public record CorrecaoRequestDTO(
            @NotNull(message = "Conteúdo é obrigatório") Map<String, Object> conteudo,
            @NotBlank(message = "Diga o motivo da correção")
            @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres") String motivo) {
    }

    public record VersaoDTO(int numero, LocalDateTime criadoEm, String autorNome, String motivo,
            ModeloFichaDTO modelo, Map<String, Object> conteudo) {
    }

    /** Registro com a versão atual (a de número mais alto). */
    public record RegistroDTO(UUID id, TipoRegistro tipo, LocalDateTime criadoEm, UUID profissionalId,
            String profissionalNome, UUID atendimentoId, LocalDateTime dataAtendimento, int versoes,
            boolean podeCorrigir, VersaoDTO atual) {
    }
}
