package com.cliniva.atendimento.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.enums.FrequenciaSerie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Agendamento recorrente: pedido, prévia das datas e resultado. */
public final class SerieDtos {

    private SerieDtos() {
    }

    /**
     * Uma série termina por data ({@code dataFim}) OU depois de
     * {@code quantidade} sessões marcadas; nunca os dois.
     */
    public record SerieRequestDTO(
            @NotNull(message = "Cliente é obrigatório") UUID clienteId,
            @NotNull(message = "Profissional é obrigatório") UUID profissionalId,
            @Size(min = 1, message = "Atendimento deve ter pelo menos um serviço") @Valid
            List<ServicoSelecionadoDTO> servicos,
            @NotNull(message = "Data e horário da primeira sessão são obrigatórios") LocalDateTime inicio,
            @NotNull(message = "Frequência é obrigatória") FrequenciaSerie frequencia,
            boolean incluiSabado,
            LocalDate dataFim,
            Integer quantidade) {
    }

    /** Uma data da série; quando não dá para marcar, o motivo. */
    public record OcorrenciaDTO(LocalDateTime dataHora, boolean disponivel, String motivo) {
    }

    public record PreviaSerieDTO(List<OcorrenciaDTO> ocorrencias, int disponiveis, int puladas) {
    }

    public record SerieCriadaDTO(UUID serieId, List<CreateAtendimentoResponseDTO> criados,
            List<OcorrenciaDTO> puladas) {
    }

    public record CancelamentoSerieDTO(int cancelados) {
    }
}
