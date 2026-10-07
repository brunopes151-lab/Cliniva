package com.cliniva.lgpd.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cliniva.atendimento.dtos.AtendimentoResponseDTO;
import com.cliniva.cliente.dtos.ClienteResponseDTO;
import com.cliniva.cliente.dtos.NotaResponseDTO;
import com.cliniva.lgpd.AcaoAuditoria;
import com.cliniva.pacote.dtos.PacoteDtos.PacoteClienteResponseDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.RegistroDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.VersaoDTO;
import com.cliniva.tenancy.Papel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LgpdDtos {

    private LgpdDtos() {
    }

    public record TermoDTO(UUID id, int versao, String texto, LocalDateTime vigenteDesde, String publicadoPor) {
    }

    public record PublicarTermoRequestDTO(@NotBlank @Size(max = 20_000) String texto) {
    }

    /** O termo aceito é o que estava na tela: se mudou no meio, o aceite é recusado. */
    public record RegistrarConsentimentoRequestDTO(@NotNull UUID termoId) {
    }

    public record RevogarConsentimentoRequestDTO(@NotBlank @Size(max = 500) String motivo) {
    }

    public record ConsentimentoDTO(UUID id, UUID termoId, int versaoTermo, boolean termoAtual,
            LocalDateTime aceitoEm, String registradoPor, boolean vigente, LocalDateTime revogadoEm,
            String revogadoPor, String motivoRevogacao) {
    }

    /** Situação do paciente: o termo atual e os aceites, do mais novo ao mais antigo. */
    public record SituacaoConsentimentoDTO(TermoDTO termoAtual, boolean vigente, boolean aceitouVersaoAtual,
            List<ConsentimentoDTO> consentimentos) {
    }

    public record AcessoDTO(LocalDateTime em, String usuario, Papel papel, boolean modoSuporte, AcaoAuditoria acao,
            UUID registroId, String ip) {
    }

    public record RegistroCompletoDTO(RegistroDTO registro, List<VersaoDTO> versoes) {
    }

    /** Tudo o que a clínica guarda do paciente, para entregar a pedido dele. */
    public record ExportacaoDTO(LocalDateTime geradoEm, String geradoPor, String clinica, ClienteResponseDTO cadastro,
            List<NotaResponseDTO> anotacoes, List<AtendimentoResponseDTO> atendimentos,
            List<PacoteClienteResponseDTO> pacotes, List<RegistroCompletoDTO> prontuario,
            List<ConsentimentoDTO> consentimentos, List<TermoDTO> termosAceitos) {
    }
}
