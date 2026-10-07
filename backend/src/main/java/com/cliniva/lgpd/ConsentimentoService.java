package com.cliniva.lgpd;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.exception.ConsentimentoPendenteException;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.TransicaoStatusInvalidaException;
import com.cliniva.lgpd.dtos.LgpdDtos.ConsentimentoDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.SituacaoConsentimentoDTO;
import com.cliniva.lgpd.dtos.LgpdDtos.TermoDTO;
import com.cliniva.prontuario.RegistroClinicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;

import lombok.RequiredArgsConstructor;

/**
 * Termo de consentimento e os aceites dos pacientes.
 *
 * <p>Vale o aceite que não foi revogado, de qualquer versão: publicar um
 * texto novo não trava o atendimento de quem aceitou o anterior, mas a
 * ficha mostra que há versão nova para colher. Quem registra é a equipe da
 * clínica; o ADMIN da plataforma, em suporte, só olha.
 */
@Service
@RequiredArgsConstructor
public class ConsentimentoService {

    private final TermoConsentimentoRepository termoRepository;
    private final ConsentimentoPacienteRepository consentimentoRepository;
    private final ClienteRepository clienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final RegistroClinicoRepository registroRepository;
    private final AuditoriaService auditoria;
    private final ClinicaContext clinicaContext;
    private final Clock clock;

    // ------------------------------------------------------------------ termo

    /** Versão atual do termo. Na primeira vez, publica o texto padrão. */
    @Transactional
    public TermoDTO termoAtual(Clinica clinica) {
        return toDto(atual(clinica));
    }

    @Transactional(readOnly = true)
    public List<TermoDTO> versoesDoTermo(Clinica clinica) {
        return termoRepository.findByClinica_IdOrderByVersaoDesc(clinica.getId()).stream()
                .map(ConsentimentoService::toDto)
                .toList();
    }

    @Transactional
    public TermoDTO publicar(Clinica clinica, String texto) {
        UsuarioPrincipal principal = equipeDaClinica();
        TermoConsentimento atual = atual(clinica);
        String limpo = texto.strip();
        if (limpo.equals(atual.getTexto())) {
            throw new IllegalArgumentException("O texto é igual ao da versão atual");
        }
        TermoConsentimento novo = new TermoConsentimento();
        novo.setClinica(clinica);
        novo.setVersao(atual.getVersao() + 1);
        novo.setTexto(limpo);
        novo.setVigenteDesde(LocalDateTime.now(clock));
        novo.setPublicadoPor(principal.nomeExibicao());
        return toDto(termoRepository.save(novo));
    }

    // ---------------------------------------------------------- consentimento

    @Transactional
    public SituacaoConsentimentoDTO situacao(Clinica clinica, UUID clienteId) {
        Cliente cliente = pacienteVisivel(clinica, clienteId);
        TermoConsentimento atual = atual(clinica);
        List<ConsentimentoDTO> lista = doPaciente(clinica, cliente.getId(), atual);
        boolean vigente = lista.stream().anyMatch(ConsentimentoDTO::vigente);
        boolean aceitouAtual = lista.stream().anyMatch(c -> c.vigente() && c.termoAtual());
        return new SituacaoConsentimentoDTO(toDto(atual), vigente, aceitouAtual, lista);
    }

    @Transactional
    public SituacaoConsentimentoDTO registrar(Clinica clinica, UUID clienteId, UUID termoId) {
        UsuarioPrincipal principal = equipeDaClinica();
        Cliente cliente = pacienteVisivel(clinica, clienteId);
        TermoConsentimento atual = atual(clinica);
        if (!atual.getId().equals(termoId)) {
            throw new IllegalArgumentException("O termo foi atualizado; leia a versão nova com o paciente");
        }
        boolean jaAceito = consentimentoRepository
                .findByClinica_IdAndCliente_IdOrderByAceitoEmDesc(clinica.getId(), cliente.getId()).stream()
                .anyMatch(c -> c.isVigente() && c.getTermo().getId().equals(atual.getId()));
        if (jaAceito) {
            throw new RecursoDuplicadoException("O paciente já aceitou esta versão do termo");
        }
        ConsentimentoPaciente consentimento = new ConsentimentoPaciente();
        consentimento.setClinica(clinica);
        consentimento.setCliente(cliente);
        consentimento.setTermo(atual);
        consentimento.setAceitoEm(LocalDateTime.now(clock));
        consentimento.setRegistradoPorId(principal.id());
        consentimento.setRegistradoPorNome(principal.nomeExibicao());
        consentimentoRepository.save(consentimento);
        auditoria.registrar(clinica, cliente.getId(), null, AcaoAuditoria.REGISTRAR_CONSENTIMENTO);
        return situacao(clinica, cliente.getId());
    }

    @Transactional
    public SituacaoConsentimentoDTO revogar(Clinica clinica, UUID consentimentoId, String motivo) {
        UsuarioPrincipal principal = equipeDaClinica();
        ConsentimentoPaciente consentimento = consentimentoRepository.findByIdAndClinica_Id(consentimentoId,
                clinica.getId()).orElseThrow(() -> new RecursoNaoEncontradoException("Consentimento não encontrado"));
        UUID clienteId = consentimento.getCliente().getId();
        pacienteVisivel(clinica, clienteId);
        if (!consentimento.isVigente()) {
            throw new TransicaoStatusInvalidaException("Este consentimento já foi revogado");
        }
        consentimento.setRevogadoEm(LocalDateTime.now(clock));
        consentimento.setRevogadoPorNome(principal.nomeExibicao());
        consentimento.setMotivoRevogacao(motivo.strip());
        auditoria.registrar(clinica, clienteId, null, AcaoAuditoria.REVOGAR_CONSENTIMENTO);
        return situacao(clinica, clienteId);
    }

    /** Registro clínico novo só com consentimento vigente. */
    @Transactional(readOnly = true)
    public void exigirVigente(Cliente cliente) {
        if (!consentimentoRepository.existsByCliente_IdAndRevogadoEmIsNull(cliente.getId())) {
            throw new ConsentimentoPendenteException(
                    "O paciente ainda não aceitou o termo de consentimento (ou revogou). "
                            + "Registre o aceite na ficha antes de escrever no prontuário.");
        }
    }

    /** Para a exportação: os aceites e os textos aceitos. */
    @Transactional
    public List<ConsentimentoDTO> doPaciente(Clinica clinica, UUID clienteId) {
        return doPaciente(clinica, clienteId, atual(clinica));
    }

    @Transactional(readOnly = true)
    public List<TermoDTO> termosAceitos(Clinica clinica, UUID clienteId) {
        return consentimentoRepository.findByClinica_IdAndCliente_IdOrderByAceitoEmDesc(clinica.getId(), clienteId)
                .stream()
                .map(ConsentimentoPaciente::getTermo)
                .distinct()
                .map(ConsentimentoService::toDto)
                .toList();
    }

    // ------------------------------------------------------------------ apoio

    private List<ConsentimentoDTO> doPaciente(Clinica clinica, UUID clienteId, TermoConsentimento atual) {
        return consentimentoRepository.findByClinica_IdAndCliente_IdOrderByAceitoEmDesc(clinica.getId(), clienteId)
                .stream()
                .map(c -> new ConsentimentoDTO(c.getId(), c.getTermo().getId(), c.getTermo().getVersao(),
                        c.getTermo().getId().equals(atual.getId()), c.getAceitoEm(), c.getRegistradoPorNome(),
                        c.isVigente(), c.getRevogadoEm(), c.getRevogadoPorNome(), c.getMotivoRevogacao()))
                .toList();
    }

    private TermoConsentimento atual(Clinica clinica) {
        return termoRepository.findFirstByClinica_IdOrderByVersaoDesc(clinica.getId()).orElseGet(() -> {
            TermoConsentimento termo = new TermoConsentimento();
            termo.setClinica(clinica);
            termo.setVersao(1);
            termo.setTexto(TermoPadrao.TEXTO);
            termo.setVigenteDesde(LocalDateTime.now(clock));
            return termoRepository.save(termo);
        });
    }

    /** Paciente da clínica; o profissional só vê os que atende. */
    private Cliente pacienteVisivel(Clinica clinica, UUID clienteId) {
        Cliente cliente = clienteRepository.findByIdAndClinica(clienteId, clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
        clinicaContext.profissionalRestrito().ifPresent(profissionalId -> {
            if (!atendimentoRepository.existsByCliente_IdAndProfissional_Id(clienteId, profissionalId)
                    && !registroRepository.existsByCliente_IdAndProfissional_Id(clienteId, profissionalId)) {
                throw new RecursoNaoEncontradoException("Cliente não encontrado");
            }
        });
        return cliente;
    }

    private UsuarioPrincipal equipeDaClinica() {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null || principal.ehAdmin()) {
            throw new AcessoNaoPermitidoException("Só a equipe da clínica registra o termo de consentimento");
        }
        return principal;
    }

    private static TermoDTO toDto(TermoConsentimento t) {
        return new TermoDTO(t.getId(), t.getVersao(), t.getTexto(), t.getVigenteDesde(), t.getPublicadoPor());
    }
}
