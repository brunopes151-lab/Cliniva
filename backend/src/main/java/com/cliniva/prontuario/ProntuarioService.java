package com.cliniva.prontuario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.atendimento.model.Atendimento;
import com.cliniva.atendimento.repository.AtendimentoRepository;
import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.lgpd.AcaoAuditoria;
import com.cliniva.lgpd.AuditoriaService;
import com.cliniva.lgpd.ConsentimentoService;
import com.cliniva.lgpd.dtos.LgpdDtos.RegistroCompletoDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.CorrecaoRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.ModeloFichaDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.NovoRegistroRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.RegistroDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.SalvarModeloRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.VersaoDTO;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Papel;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalRepository;
import com.cliniva.tenancy.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Prontuário: modelos de ficha, registros e versões.
 *
 * <p>Quem lê: administração (e o ADMIN da plataforma em suporte) lê todos os
 * pacientes; o profissional só os que atende. A recepção não vê conteúdo
 * clínico. Quem escreve: só quem tem cadastro de profissional, e corrigir é
 * só do profissional que fez o registro. Nada se apaga: corrigir grava uma
 * versão nova com o motivo.
 *
 * <p>LGPD: toda leitura e escrita fica na auditoria (inclusive do ADMIN em
 * suporte), e registro novo exige termo de consentimento vigente.
 */
@Service
@RequiredArgsConstructor
public class ProntuarioService {

    static final int LIMITE_TEXTO = 10_000;
    private static final Pattern ID_CAMPO = Pattern.compile("[a-z][a-z0-9_]{0,39}");

    private final ModeloFichaRepository modeloRepository;
    private final RegistroClinicoRepository registroRepository;
    private final RegistroClinicoVersaoRepository versaoRepository;
    private final ClienteRepository clienteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClinicaContext clinicaContext;
    private final AuditoriaService auditoria;
    private final ConsentimentoService consentimentos;
    private final Clock clock;

    // ---------------------------------------------------------------- modelos

    /** Versão atual de cada modelo. Na primeira vez, cria os modelos padrão. */
    @Transactional
    public List<ModeloFichaDTO> listarModelos(Clinica clinica, boolean incluirInativos) {
        garantirPadrao(clinica);
        return atuais(clinica).stream()
                .filter(m -> incluirInativos || m.isAtivo())
                .map(ProntuarioService::toDto)
                .toList();
    }

    @Transactional
    public ModeloFichaDTO criarModelo(Clinica clinica, SalvarModeloRequestDTO request) {
        garantirPadrao(clinica);
        UUID familia = UUID.randomUUID();
        validarNome(clinica, request.nome(), familia);
        return toDto(salvarVersao(clinica, familia, 1, request));
    }

    /** Editar um modelo grava a versão seguinte; registros antigos seguem na versão que usaram. */
    @Transactional
    public ModeloFichaDTO novaVersao(Clinica clinica, UUID familiaId, SalvarModeloRequestDTO request) {
        ModeloFicha atual = versoesDaFamilia(clinica, familiaId).get(0);
        if (atual.getTipo() != request.tipo()) {
            throw new IllegalArgumentException("O tipo de registro do modelo não muda; crie um modelo novo");
        }
        validarNome(clinica, request.nome(), familiaId);
        ModeloFicha nova = salvarVersao(clinica, familiaId, atual.getVersao() + 1, request);
        nova.setAtivo(atual.isAtivo());
        return toDto(nova);
    }

    @Transactional
    public ModeloFichaDTO definirAtivo(Clinica clinica, UUID familiaId, boolean ativo) {
        List<ModeloFicha> versoes = versoesDaFamilia(clinica, familiaId);
        versoes.forEach(m -> m.setAtivo(ativo));
        return toDto(versoes.get(0));
    }

    // ------------------------------------------------------------- prontuário

    @Transactional
    public List<RegistroDTO> listar(Clinica clinica, UUID clienteId) {
        Cliente cliente = pacienteLegivel(clinica, clienteId);
        auditoria.registrar(clinica, cliente.getId(), null, AcaoAuditoria.VER_PRONTUARIO);
        List<RegistroClinico> registros = registroRepository
                .findByClinica_IdAndCliente_IdOrderByCriadoEmDesc(clinica.getId(), cliente.getId());
        if (registros.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RegistroClinicoVersao>> versoes = versaoRepository
                .findByRegistro_IdInOrderByNumeroDesc(registros.stream().map(RegistroClinico::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(v -> v.getRegistro().getId()));
        return registros.stream()
                .map(r -> toDto(r, versoes.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional
    public List<VersaoDTO> historico(Clinica clinica, UUID registroId) {
        RegistroClinico registro = registroLegivel(clinica, registroId);
        auditoria.registrar(clinica, registro.getCliente().getId(), registro.getId(), AcaoAuditoria.VER_HISTORICO);
        return versaoRepository.findByRegistro_IdOrderByNumeroDesc(registro.getId()).stream()
                .map(ProntuarioService::toDto)
                .toList();
    }

    @Transactional
    public RegistroDTO registrar(Clinica clinica, UUID clienteId, NovoRegistroRequestDTO request) {
        UsuarioPrincipal autor = autorClinico();
        Cliente cliente = pacienteLegivel(clinica, clienteId);
        consentimentos.exigirVigente(cliente);
        Profissional profissional = profissionalRepository.findByIdAndClinica_Id(autor.profissionalId(), clinica.getId())
                .orElseThrow(() -> new AcessoNaoPermitidoException("Seu acesso não está ligado a um profissional"));

        ModeloFicha modelo = modeloRepository.findByIdAndClinica_Id(request.modeloId(), clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Modelo de ficha não encontrado"));
        ModeloFicha atual = versoesDaFamilia(clinica, modelo.getFamiliaId()).get(0);
        if (!atual.getId().equals(modelo.getId()) || !modelo.isAtivo()) {
            throw new IllegalArgumentException("Este modelo foi atualizado ou desativado; recarregue a ficha");
        }

        Atendimento atendimento = null;
        if (request.atendimentoId() != null) {
            atendimento = atendimentoRepository.findById(request.atendimentoId())
                    .filter(a -> a.getClinica().getId().equals(clinica.getId()))
                    .filter(a -> a.getCliente().getId().equals(cliente.getId()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento não encontrado"));
        }

        RegistroClinico registro = new RegistroClinico();
        registro.setClinica(clinica);
        registro.setCliente(cliente);
        registro.setProfissional(profissional);
        registro.setAtendimento(atendimento);
        registro.setTipo(modelo.getTipo());
        registro.setCriadoEm(LocalDateTime.now(clock));
        registroRepository.save(registro);

        RegistroClinicoVersao versao = gravarVersao(clinica, registro, 1, modelo, request.conteudo(), null, autor);
        auditoria.registrar(clinica, cliente.getId(), registro.getId(), AcaoAuditoria.CRIAR_REGISTRO);
        return toDto(registro, List.of(versao));
    }

    @Transactional
    public RegistroDTO corrigir(Clinica clinica, UUID registroId, CorrecaoRequestDTO request) {
        UsuarioPrincipal autor = autorClinico();
        RegistroClinico registro = registroLegivel(clinica, registroId);
        if (!registro.getProfissional().getId().equals(autor.profissionalId())) {
            throw new AcessoNaoPermitidoException("Só o profissional que fez o registro pode corrigi-lo");
        }
        List<RegistroClinicoVersao> versoes = new ArrayList<>(
                versaoRepository.findByRegistro_IdOrderByNumeroDesc(registro.getId()));
        RegistroClinicoVersao ultima = versoes.get(0);
        // A correção usa o mesmo formulário da versão anterior.
        RegistroClinicoVersao nova = gravarVersao(clinica, registro, ultima.getNumero() + 1, ultima.getModelo(),
                request.conteudo(), request.motivo().trim(), autor);
        versoes.add(0, nova);
        auditoria.registrar(clinica, registro.getCliente().getId(), registro.getId(),
                AcaoAuditoria.CORRIGIR_REGISTRO);
        return toDto(registro, versoes);
    }

    /**
     * Prontuário inteiro, com todas as versões, para a exportação a pedido do
     * paciente. Quem chama confere a permissão e audita a exportação.
     */
    @Transactional(readOnly = true)
    public List<RegistroCompletoDTO> exportar(Clinica clinica, UUID clienteId) {
        List<RegistroClinico> registros = registroRepository
                .findByClinica_IdAndCliente_IdOrderByCriadoEmDesc(clinica.getId(), clienteId);
        if (registros.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<RegistroClinicoVersao>> versoes = versaoRepository
                .findByRegistro_IdInOrderByNumeroDesc(registros.stream().map(RegistroClinico::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(v -> v.getRegistro().getId()));
        return registros.stream()
                .map(r -> {
                    List<RegistroClinicoVersao> doRegistro = versoes.getOrDefault(r.getId(), List.of());
                    return new RegistroCompletoDTO(toDto(r, doRegistro),
                            doRegistro.stream().map(ProntuarioService::toDto).toList());
                })
                .toList();
    }

    // ------------------------------------------------------------ permissões

    /** Recepção não vê conteúdo clínico; profissional só o paciente que atende. */
    private Cliente pacienteLegivel(Clinica clinica, UUID clienteId) {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null || principal.papel() == Papel.RECEPCAO) {
            throw new AcessoNaoPermitidoException("Seu perfil não tem acesso ao prontuário");
        }
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

    private RegistroClinico registroLegivel(Clinica clinica, UUID registroId) {
        RegistroClinico registro = registroRepository.findByIdAndClinica_Id(registroId, clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Registro não encontrado"));
        pacienteLegivel(clinica, registro.getCliente().getId());
        return registro;
    }

    /** Escrever no prontuário exige um acesso ligado a um profissional (o ADMIN da plataforma não escreve). */
    private UsuarioPrincipal autorClinico() {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null || principal.ehAdmin() || principal.papel() == Papel.RECEPCAO
                || principal.profissionalId() == null) {
            throw new AcessoNaoPermitidoException("Só profissionais registram no prontuário");
        }
        return principal;
    }

    // ------------------------------------------------------------- gravação

    private RegistroClinicoVersao gravarVersao(Clinica clinica, RegistroClinico registro, int numero,
            ModeloFicha modelo, Map<String, Object> conteudo, String motivo, UsuarioPrincipal autor) {
        RegistroClinicoVersao versao = new RegistroClinicoVersao();
        versao.setClinica(clinica);
        versao.setRegistro(registro);
        versao.setNumero(numero);
        versao.setModelo(modelo);
        versao.setConteudo(validarConteudo(modelo, conteudo));
        versao.setAutor(usuarioRepository.getReferenceById(autor.id()));
        versao.setAutorNome(autor.nomeExibicao());
        versao.setMotivo(motivo);
        versao.setCriadoEm(LocalDateTime.now(clock));
        return versaoRepository.save(versao);
    }

    /**
     * Confere as respostas contra o modelo. Campos vazios não são gravados;
     * pergunta que não existe no modelo é recusada.
     */
    static Map<String, Object> validarConteudo(ModeloFicha modelo, Map<String, Object> conteudo) {
        Map<String, CampoFicha> campos = new LinkedHashMap<>();
        modelo.getCampos().forEach(c -> campos.put(c.id(), c));
        for (String chave : conteudo.keySet()) {
            if (!campos.containsKey(chave)) {
                throw new IllegalArgumentException("Pergunta desconhecida neste modelo: " + chave);
            }
        }
        Map<String, Object> limpo = new LinkedHashMap<>();
        for (CampoFicha campo : campos.values()) {
            Object valor = conteudo.get(campo.id());
            if (valor == null || (valor instanceof String s && s.isBlank())) {
                if (campo.obrigatorio()) {
                    throw new IllegalArgumentException("Preencha \"" + campo.rotulo() + "\"");
                }
                continue;
            }
            limpo.put(campo.id(), valorValido(campo, valor));
        }
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Preencha pelo menos uma pergunta");
        }
        return limpo;
    }

    private static Object valorValido(CampoFicha campo, Object valor) {
        String erro = "Resposta inválida em \"" + campo.rotulo() + "\"";
        switch (campo.tipo()) {
            case TEXTO, TEXTO_LONGO -> {
                if (!(valor instanceof String texto) || texto.length() > LIMITE_TEXTO) {
                    throw new IllegalArgumentException(erro);
                }
                return texto.strip();
            }
            case NUMERO -> {
                if (!(valor instanceof Number numero)) {
                    throw new IllegalArgumentException(erro);
                }
                return numero;
            }
            case ESCALA -> {
                if (!(valor instanceof Number numero) || numero.doubleValue() != numero.intValue()
                        || numero.intValue() < 0 || numero.intValue() > 10) {
                    throw new IllegalArgumentException(erro + ": use um número de 0 a 10");
                }
                return numero.intValue();
            }
            case SIM_NAO -> {
                if (!(valor instanceof Boolean)) {
                    throw new IllegalArgumentException(erro);
                }
                return valor;
            }
            case OPCOES -> {
                if (!(valor instanceof String opcao) || campo.opcoes() == null || !campo.opcoes().contains(opcao)) {
                    throw new IllegalArgumentException(erro);
                }
                return opcao;
            }
            case DATA -> {
                try {
                    return LocalDate.parse((String) valor).toString();
                } catch (ClassCastException | DateTimeParseException e) {
                    throw new IllegalArgumentException(erro);
                }
            }
            default -> throw new IllegalArgumentException(erro);
        }
    }

    // ---------------------------------------------------------- modelos: apoio

    private void garantirPadrao(Clinica clinica) {
        if (modeloRepository.existsByClinica_Id(clinica.getId())) {
            return;
        }
        for (ModelosFichaPadrao.Padrao padrao : ModelosFichaPadrao.TODOS) {
            salvarVersao(clinica, UUID.randomUUID(), 1,
                    new SalvarModeloRequestDTO(padrao.nome(), padrao.area(), padrao.tipo(), padrao.campos()));
        }
    }

    private List<ModeloFicha> atuais(Clinica clinica) {
        Set<UUID> vistas = new HashSet<>();
        return modeloRepository.findByClinica_IdOrderByVersaoDesc(clinica.getId()).stream()
                .filter(m -> vistas.add(m.getFamiliaId()))
                .sorted(Comparator.comparing(ModeloFicha::getNome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ModeloFicha> versoesDaFamilia(Clinica clinica, UUID familiaId) {
        List<ModeloFicha> versoes = modeloRepository.findByClinica_IdAndFamiliaIdOrderByVersaoDesc(
                clinica.getId(), familiaId);
        if (versoes.isEmpty()) {
            throw new RecursoNaoEncontradoException("Modelo de ficha não encontrado");
        }
        return versoes;
    }

    private void validarNome(Clinica clinica, String nome, UUID familiaId) {
        if (modeloRepository.existsByClinica_IdAndNomeIgnoreCaseAndFamiliaIdNot(clinica.getId(), nome.trim(),
                familiaId)) {
            throw new RecursoDuplicadoException("Já existe um modelo com esse nome");
        }
    }

    private ModeloFicha salvarVersao(Clinica clinica, UUID familiaId, int versao, SalvarModeloRequestDTO request) {
        ModeloFicha modelo = new ModeloFicha();
        modelo.setClinica(clinica);
        modelo.setFamiliaId(familiaId);
        modelo.setVersao(versao);
        modelo.setNome(request.nome().trim());
        modelo.setArea(request.area());
        modelo.setTipo(request.tipo());
        modelo.setCampos(validarCampos(request.campos()));
        modelo.setCriadoEm(LocalDateTime.now(clock));
        return modeloRepository.save(modelo);
    }

    static List<CampoFicha> validarCampos(List<CampoFicha> campos) {
        Set<String> ids = new HashSet<>();
        List<CampoFicha> limpos = new ArrayList<>();
        for (CampoFicha c : campos) {
            if (c == null || c.id() == null || !ID_CAMPO.matcher(c.id()).matches()) {
                throw new IllegalArgumentException(
                        "Identificador de pergunta inválido: use letras minúsculas, números e _");
            }
            if (!ids.add(c.id())) {
                throw new IllegalArgumentException("Pergunta repetida: " + c.id());
            }
            if (c.rotulo() == null || c.rotulo().isBlank() || c.rotulo().length() > 200) {
                throw new IllegalArgumentException("Toda pergunta precisa de um texto de até 200 caracteres");
            }
            if (c.tipo() == null) {
                throw new IllegalArgumentException("Escolha o tipo da pergunta \"" + c.rotulo() + "\"");
            }
            List<String> opcoes = null;
            if (c.tipo() == TipoCampo.OPCOES) {
                if (c.opcoes() == null || c.opcoes().size() < 2
                        || c.opcoes().stream().anyMatch(o -> o == null || o.isBlank())) {
                    throw new IllegalArgumentException(
                            "A pergunta \"" + c.rotulo() + "\" precisa de pelo menos duas opções");
                }
                opcoes = c.opcoes().stream().map(String::strip).distinct().toList();
            }
            limpos.add(new CampoFicha(c.id(), c.rotulo().strip(), c.tipo(), c.obrigatorio(), opcoes));
        }
        return limpos;
    }

    // ------------------------------------------------------------- conversão

    static ModeloFichaDTO toDto(ModeloFicha m) {
        return new ModeloFichaDTO(m.getId(), m.getFamiliaId(), m.getVersao(), m.getNome(), m.getArea(),
                m.getTipo(), m.getCampos(), m.isAtivo());
    }

    private static VersaoDTO toDto(RegistroClinicoVersao v) {
        return new VersaoDTO(v.getNumero(), v.getCriadoEm(), v.getAutorNome(), v.getMotivo(),
                toDto(v.getModelo()), v.getConteudo());
    }

    private RegistroDTO toDto(RegistroClinico r, List<RegistroClinicoVersao> versoes) {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        boolean podeCorrigir = principal != null && !principal.ehAdmin()
                && r.getProfissional().getId().equals(principal.profissionalId());
        Atendimento atendimento = r.getAtendimento();
        return new RegistroDTO(r.getId(), r.getTipo(), r.getCriadoEm(), r.getProfissional().getId(),
                r.getProfissional().getNome(), atendimento == null ? null : atendimento.getId(),
                atendimento == null ? null : atendimento.getDataAtendimento(), versoes.size(), podeCorrigir,
                versoes.isEmpty() ? null : toDto(versoes.get(0)));
    }
}
