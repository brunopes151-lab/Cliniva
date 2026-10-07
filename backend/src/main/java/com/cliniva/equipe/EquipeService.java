package com.cliniva.equipe;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.AgendaService;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.Especialidade;
import com.cliniva.tenancy.EspecialidadeRepository;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalRepository;
import com.cliniva.tenancy.ProfissionalService;
import com.cliniva.tenancy.dtos.EquipeDtos.EspecialidadeDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.ProfissionalResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarEspecialidadeRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarProfissionalRequestDTO;

import lombok.RequiredArgsConstructor;

/** Cadastro de profissionais e especialidades da clínica. */
@Service
@RequiredArgsConstructor
public class EquipeService {

    private final ProfissionalRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalService profissionalService;
    private final AgendaService agendaService;

    @Transactional(readOnly = true)
    public List<ProfissionalResponseDTO> listarProfissionais(Clinica clinica) {
        return profissionalRepository.findByClinica_IdOrderByNomeAsc(clinica.getId()).stream()
                .map(EquipeService::toDTO)
                .toList();
    }

    /** Cria o profissional já com o expediente padrão da clínica (o do "Geral"). */
    @Transactional
    public ProfissionalResponseDTO criarProfissional(Clinica clinica, SalvarProfissionalRequestDTO request) {
        String nome = request.nome().trim();
        if (profissionalRepository.existsByClinica_IdAndNomeIgnoreCase(clinica.getId(), nome)) {
            throw new RecursoDuplicadoException("Já existe um profissional com esse nome");
        }
        Profissional profissional = new Profissional();
        profissional.setClinica(clinica);
        preencher(clinica, profissional, nome, request);
        profissionalRepository.save(profissional);
        agendaService.copiarExpedienteGeral(clinica, profissional);
        return toDTO(profissional);
    }

    @Transactional
    public ProfissionalResponseDTO atualizarProfissional(Clinica clinica, UUID id,
            SalvarProfissionalRequestDTO request) {
        Profissional profissional = profissionalService.buscar(clinica, id);
        String nome = request.nome().trim();
        if (profissional.ehGeral() && !nome.equalsIgnoreCase(ProfissionalService.NOME_GERAL)) {
            // garantirGeral procura pelo nome; renomear criaria um segundo "Geral".
            throw new IllegalArgumentException("O profissional Geral não pode ser renomeado");
        }
        if (profissionalRepository.existsByClinica_IdAndNomeIgnoreCaseAndIdNot(clinica.getId(), nome, id)) {
            throw new RecursoDuplicadoException("Já existe um profissional com esse nome");
        }
        preencher(clinica, profissional, nome, request);
        return toDTO(profissionalRepository.save(profissional));
    }

    @Transactional(readOnly = true)
    public List<EspecialidadeDTO> listarEspecialidades(Clinica clinica) {
        return especialidadeRepository.findByClinica_IdOrderByNomeAsc(clinica.getId()).stream()
                .map(e -> new EspecialidadeDTO(e.getId(), e.getNome()))
                .toList();
    }

    @Transactional
    public EspecialidadeDTO criarEspecialidade(Clinica clinica, SalvarEspecialidadeRequestDTO request) {
        String nome = request.nome().trim();
        if (especialidadeRepository.existsByClinica_IdAndNomeIgnoreCase(clinica.getId(), nome)) {
            throw new RecursoDuplicadoException("Especialidade já cadastrada");
        }
        Especialidade especialidade = new Especialidade();
        especialidade.setClinica(clinica);
        especialidade.setNome(nome);
        especialidadeRepository.save(especialidade);
        return new EspecialidadeDTO(especialidade.getId(), especialidade.getNome());
    }

    @Transactional
    public EspecialidadeDTO renomearEspecialidade(Clinica clinica, UUID id, SalvarEspecialidadeRequestDTO request) {
        Especialidade especialidade = buscarEspecialidade(clinica, id);
        String nome = request.nome().trim();
        if (especialidadeRepository.existsByClinica_IdAndNomeIgnoreCaseAndIdNot(clinica.getId(), nome, id)) {
            throw new RecursoDuplicadoException("Especialidade já cadastrada");
        }
        especialidade.setNome(nome);
        return new EspecialidadeDTO(especialidade.getId(), especialidadeRepository.save(especialidade).getNome());
    }

    /** Apaga a especialidade e tira dos profissionais que a tinham. */
    @Transactional
    public void excluirEspecialidade(Clinica clinica, UUID id) {
        Especialidade especialidade = buscarEspecialidade(clinica, id);
        for (Profissional profissional : profissionalRepository.findByClinica_IdOrderByNomeAsc(clinica.getId())) {
            if (profissional.getEspecialidades().remove(especialidade)) {
                profissionalRepository.save(profissional);
            }
        }
        especialidadeRepository.delete(especialidade);
    }

    private void preencher(Clinica clinica, Profissional profissional, String nome,
            SalvarProfissionalRequestDTO request) {
        profissional.setNome(nome);
        profissional.setCor(request.cor() == null || request.cor().isBlank() ? null : request.cor());
        profissional.setAtivo(request.ativo() == null || request.ativo());
        profissional.setEspecialidades(resolverEspecialidades(clinica, request.especialidadeIds()));
        profissional.setServicos(resolverServicos(clinica, request.servicoIds()));
    }

    private Set<Especialidade> resolverEspecialidades(Clinica clinica, List<UUID> ids) {
        Set<Especialidade> especialidades = new HashSet<>();
        if (ids != null) {
            for (UUID id : ids) {
                especialidades.add(buscarEspecialidade(clinica, id));
            }
        }
        return especialidades;
    }

    private Set<Servico> resolverServicos(Clinica clinica, List<UUID> ids) {
        Set<Servico> servicos = new HashSet<>();
        if (ids != null) {
            for (UUID id : ids) {
                servicos.add(servicoRepository.findByIdAndClinica(id, clinica)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado")));
            }
        }
        return servicos;
    }

    private Especialidade buscarEspecialidade(Clinica clinica, UUID id) {
        return especialidadeRepository.findByIdAndClinica_Id(id, clinica.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Especialidade não encontrada"));
    }

    static ProfissionalResponseDTO toDTO(Profissional profissional) {
        return new ProfissionalResponseDTO(
                profissional.getId(),
                profissional.getNome(),
                profissional.getCor(),
                profissional.isAtivo(),
                profissional.ehGeral(),
                profissional.getEspecialidades().stream()
                        .sorted(Comparator.comparing(Especialidade::getNome))
                        .map(e -> new EspecialidadeDTO(e.getId(), e.getNome()))
                        .toList(),
                profissional.getServicos().stream().map(Servico::getId).sorted().toList());
    }
}
