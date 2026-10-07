package com.cliniva.pacote.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cliniva.pacote.SituacaoPacote;
import com.cliniva.pacote.TipoMovimentoPacote;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Modelos de pacote e pacotes dos pacientes. */
public final class PacoteDtos {

    private PacoteDtos() {
    }

    public record PacoteResponseDTO(UUID id, String nome, UUID servicoId, String servicoNome, int sessoes,
            int validadeDias, BigDecimal preco, boolean ativo) {
    }

    public record SalvarPacoteRequestDTO(
            @NotBlank(message = "Nome do pacote é obrigatório")
            @Size(max = 120, message = "Nome deve ter no máximo 120 caracteres") String nome,
            @NotNull(message = "Serviço é obrigatório") UUID servicoId,
            @NotNull(message = "Número de sessões é obrigatório")
            @Min(value = 1, message = "O pacote precisa ter pelo menos 1 sessão")
            @Max(value = 100, message = "O pacote pode ter no máximo 100 sessões") Integer sessoes,
            @NotNull(message = "Validade é obrigatória")
            @Min(value = 1, message = "Validade mínima de 1 dia")
            @Max(value = 1095, message = "Validade máxima de 3 anos") Integer validadeDias,
            @NotNull(message = "Preço é obrigatório")
            @DecimalMin(value = "0.00", message = "Preço não pode ser negativo") BigDecimal preco,
            Boolean ativo) {
    }

    /** Venda de um pacote ao paciente. Sem data, é hoje; sem valor, o preço do modelo. */
    public record VenderPacoteRequestDTO(
            @NotNull(message = "Pacote é obrigatório") UUID pacoteId,
            LocalDate dataCompra,
            @DecimalMin(value = "0.00", message = "Valor não pode ser negativo") BigDecimal valorPago) {
    }

    public record MovimentoPacoteDTO(TipoMovimentoPacote tipo, LocalDateTime criadoEm, UUID atendimentoId,
            LocalDateTime dataAtendimento) {
    }

    public record PacoteClienteResponseDTO(UUID id, UUID pacoteId, String nome, UUID servicoId,
            String servicoNome, int sessoesTotal, int saldo, LocalDate dataCompra, LocalDate dataValidade,
            BigDecimal valorPago, SituacaoPacote situacao, List<MovimentoPacoteDTO> movimentos) {
    }
}
