package com.cliniva.lgpd;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.cliente.ClienteService;
import com.cliniva.cliente.dtos.ClienteHistoricoResponseDTO;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.lgpd.dtos.LgpdDtos.ExportacaoDTO;
import com.cliniva.pacote.PacoteService;
import com.cliniva.prontuario.ProntuarioService;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Papel;

import lombok.RequiredArgsConstructor;

/**
 * Exportação dos dados do paciente (art. 18 da LGPD). Só o administrador
 * da clínica exporta; o ADMIN da plataforma não tira dados de paciente.
 */
@Service
@RequiredArgsConstructor
public class ExportacaoService {

    private final ClienteService clienteService;
    private final PacoteService pacoteService;
    private final ProntuarioService prontuarioService;
    private final ConsentimentoService consentimentoService;
    private final AuditoriaService auditoria;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    @Transactional
    public ExportacaoDTO exportar(Clinica clinica, UUID clienteId) {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null || principal.papel() != Papel.OWNER) {
            throw new AcessoNaoPermitidoException("Só o administrador da clínica exporta os dados do paciente");
        }
        ClienteHistoricoResponseDTO historico = clienteService.historicoCliente(clinica, clienteId);
        ExportacaoDTO exportacao = new ExportacaoDTO(
                LocalDateTime.now(clock),
                principal.nomeExibicao(),
                clinica.getNome(),
                historico.cliente(),
                clienteService.listarNotas(clinica, clienteId),
                historico.atendimentos(),
                pacoteService.listarDoCliente(clinica, clienteId),
                prontuarioService.exportar(clinica, clienteId),
                consentimentoService.doPaciente(clinica, clienteId),
                consentimentoService.termosAceitos(clinica, clienteId));
        auditoria.registrar(clinica, clienteId, null, AcaoAuditoria.EXPORTAR_DADOS);
        return exportacao;
    }
}
