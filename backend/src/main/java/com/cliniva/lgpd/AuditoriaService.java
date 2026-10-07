package com.cliniva.lgpd;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.common.OrigemRequisicao;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.lgpd.dtos.LgpdDtos.AcessoDTO;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;

import lombok.RequiredArgsConstructor;

/**
 * Rastro de quem leu, escreveu ou exportou dados clínicos. Grava na mesma
 * transação da operação: se a operação falha, não fica registro dela.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaAcessoRepository repository;
    private final ClienteRepository clienteRepository;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    @Transactional
    public void registrar(Clinica clinica, UUID clienteId, UUID registroId, AcaoAuditoria acao) {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null) {
            throw new AcessoNaoPermitidoException("Acesso a dados de saúde exige login");
        }
        AuditoriaAcesso linha = new AuditoriaAcesso();
        linha.setClinica(clinica);
        linha.setClienteId(clienteId);
        linha.setRegistroId(registroId);
        linha.setUsuarioId(principal.id());
        linha.setUsuarioNome(principal.nomeExibicao());
        linha.setPapel(principal.papel());
        linha.setModoSuporte(principal.ehAdmin());
        linha.setAcao(acao);
        linha.setIp(ipAtual());
        linha.setCriadoEm(LocalDateTime.now(clock));
        repository.save(linha);
    }

    @Transactional(readOnly = true)
    public List<AcessoDTO> listar(Clinica clinica, UUID clienteId) {
        if (!clienteRepository.existsByIdAndClinica(clienteId, clinica)) {
            throw new RecursoNaoEncontradoException("Cliente não encontrado");
        }
        return repository.findByClinica_IdAndClienteIdOrderByCriadoEmDesc(clinica.getId(), clienteId).stream()
                .map(a -> new AcessoDTO(a.getCriadoEm(), a.getUsuarioNome(), a.getPapel(), a.isModoSuporte(),
                        a.getAcao(), a.getRegistroId(), a.getIp()))
                .toList();
    }

    private static String ipAtual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ip = OrigemRequisicao.ip(attrs.getRequest());
            return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
        }
        return null;
    }
}
