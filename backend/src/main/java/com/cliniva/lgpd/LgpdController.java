package com.cliniva.lgpd;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.lgpd.dtos.LgpdDtos.AcessoDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.ExportacaoDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.PublicarTermoRequestDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.RegistrarConsentimentoRequestDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.RevogarConsentimentoRequestDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.SituacaoConsentimentoDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.TermoDTO;
import com.cliniva.tenancy.ClinicaContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Termo de consentimento, aceites, auditoria e exportação. Os perfis de
 * cada rota ficam no SecurityConfig; o resto das regras, nos serviços.
 */
@RestController
@RequiredArgsConstructor
public class LgpdController {

    private final ConsentimentoService consentimentos;
    private final AuditoriaService auditoria;
    private final ExportacaoService exportacao;
    private final ClinicaContext clinicaContext;

    @GetMapping("/api/lgpd/termo")
    public TermoDTO termo() {
        return consentimentos.termoAtual(clinicaContext.obterClinicaAtual());
    }

    @GetMapping("/api/lgpd/termo/versoes")
    public List<TermoDTO> versoesDoTermo() {
        return consentimentos.versoesDoTermo(clinicaContext.obterClinicaAtual());
    }

    @PostMapping("/api/lgpd/termo")
    public TermoDTO publicar(@Valid @RequestBody PublicarTermoRequestDTO request) {
        return consentimentos.publicar(clinicaContext.obterClinicaAtual(), request.texto());
    }

    @GetMapping("/api/clientes/{clienteId}/consentimentos")
    public SituacaoConsentimentoDTO situacao(@PathVariable UUID clienteId) {
        return consentimentos.situacao(clinicaContext.obterClinicaAtual(), clienteId);
    }

    @PostMapping("/api/clientes/{clienteId}/consentimentos")
    public SituacaoConsentimentoDTO registrar(@PathVariable UUID clienteId,
            @Valid @RequestBody RegistrarConsentimentoRequestDTO request) {
        return consentimentos.registrar(clinicaContext.obterClinicaAtual(), clienteId, request.termoId());
    }

    @PostMapping("/api/consentimentos/{consentimentoId}/revogacao")
    public SituacaoConsentimentoDTO revogar(@PathVariable UUID consentimentoId,
            @Valid @RequestBody RevogarConsentimentoRequestDTO request) {
        return consentimentos.revogar(clinicaContext.obterClinicaAtual(), consentimentoId, request.motivo());
    }

    @GetMapping("/api/clientes/{clienteId}/auditoria")
    public List<AcessoDTO> auditoria(@PathVariable UUID clienteId) {
        return auditoria.listar(clinicaContext.obterClinicaAtual(), clienteId);
    }

    @GetMapping("/api/clientes/{clienteId}/exportacao")
    public ResponseEntity<ExportacaoDTO> exportar(@PathVariable UUID clienteId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(exportacao.exportar(clinicaContext.obterClinicaAtual(), clienteId));
    }
}
