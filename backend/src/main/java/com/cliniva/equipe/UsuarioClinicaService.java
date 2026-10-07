package com.cliniva.equipe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.exception.SupabaseIndisponivelException;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.Papel;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;
import com.cliniva.tenancy.SupabaseUsersService;
import com.cliniva.tenancy.SupabaseUsersService.UsuarioSupabase;
import com.cliniva.tenancy.Usuario;
import com.cliniva.tenancy.UsuarioRepository;
import com.cliniva.tenancy.dtos.EquipeDtos.AtualizarUsuarioClinicaRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.CriarUsuarioClinicaRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SenhaTemporariaResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.UsuarioClinicaResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.UsuarioCriadoResponseDTO;

import lombok.RequiredArgsConstructor;

/**
 * Quem acessa o sistema da clínica e com qual perfil. Só o administrador
 * da clínica (ou o ADMIN da plataforma em modo suporte) chega aqui.
 */
@Service
@RequiredArgsConstructor
public class UsuarioClinicaService {

    private final UsuarioRepository usuarioRepository;
    private final ProfissionalService profissionalService;
    private final SupabaseUsersService supabaseUsers;
    private final ClinicaContext clinicaContext;

    @Transactional(readOnly = true)
    public List<UsuarioClinicaResponseDTO> listar(Clinica clinica) {
        return usuarioRepository.findByClinica_IdOrderByNomeAsc(clinica.getId()).stream()
                .filter(usuario -> usuario.getPapel() != Papel.ADMIN)
                .map(UsuarioClinicaService::toDTO)
                .toList();
    }

    /**
     * Cria o acesso. Com o Supabase configurado, cria também a conta de login
     * e devolve uma senha temporária; sem ele, a pessoa entra com uma conta
     * do Supabase de mesmo e-mail criada por fora.
     */
    @Transactional
    public UsuarioCriadoResponseDTO criar(Clinica clinica, CriarUsuarioClinicaRequestDTO request) {
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new RecursoDuplicadoException("E-mail já cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setClinica(clinica);
        usuario.setEmail(email);
        usuario.setNome(request.nome().trim());
        usuario.setAtivo(true);
        aplicarPerfil(clinica, usuario, request.papel(), request.profissionalId());

        String senhaTemporaria = null;
        if (supabaseUsers.configurada()) {
            Optional<UsuarioSupabase> existente = supabaseUsers.buscarPorEmail(email);
            if (existente.isPresent()) {
                usuario.setSupabaseUserId(existente.get().id());
            } else {
                senhaTemporaria = gerarSenhaTemporaria();
                usuario.setSupabaseUserId(supabaseUsers.criarUsuario(email, senhaTemporaria).id());
            }
        }
        usuarioRepository.save(usuario);
        return new UsuarioCriadoResponseDTO(toDTO(usuario), senhaTemporaria);
    }

    @Transactional
    public UsuarioClinicaResponseDTO atualizar(Clinica clinica, UUID id, AtualizarUsuarioClinicaRequestDTO request) {
        Usuario usuario = buscar(clinica, id);
        UsuarioPrincipal logado = clinicaContext.principalAtual();
        boolean ehOProprio = logado != null && logado.id().equals(usuario.getId());
        if (ehOProprio && (request.papel() != Papel.OWNER || !request.ativo())) {
            // Evita a clínica ficar sem ninguém que consiga administrar.
            throw new IllegalArgumentException("Você não pode tirar o seu próprio acesso de administrador");
        }
        usuario.setNome(request.nome().trim());
        usuario.setAtivo(request.ativo());
        aplicarPerfil(clinica, usuario, request.papel(), request.profissionalId());
        return toDTO(usuarioRepository.save(usuario));
    }

    @Transactional
    public SenhaTemporariaResponseDTO resetarSenha(Clinica clinica, UUID id) {
        Usuario usuario = buscar(clinica, id);
        if (usuario.getSupabaseUserId() == null) {
            throw new SupabaseIndisponivelException("Usuário ainda não entrou nenhuma vez no sistema");
        }
        String novaSenha = gerarSenhaTemporaria();
        supabaseUsers.definirSenha(usuario.getSupabaseUserId(), novaSenha);
        return new SenhaTemporariaResponseDTO(usuario.getEmail(), novaSenha);
    }

    /**
     * PROFISSIONAL precisa de um profissional (é o que limita o que ele vê);
     * OWNER pode ter um (o dono que também atende); RECEPCAO não tem.
     */
    private void aplicarPerfil(Clinica clinica, Usuario usuario, Papel papel, UUID profissionalId) {
        if (papel == Papel.ADMIN) {
            throw new IllegalArgumentException("Perfil inválido para usuário da clínica");
        }
        if (papel == Papel.PROFISSIONAL && profissionalId == null) {
            throw new IllegalArgumentException("Escolha o profissional ligado a este acesso");
        }
        Profissional profissional = null;
        if (papel != Papel.RECEPCAO && profissionalId != null) {
            profissional = profissionalService.buscar(clinica, profissionalId);
            Optional<Usuario> outro = usuarioRepository.findByProfissional_Id(profissional.getId())
                    .filter(existente -> !existente.getId().equals(usuario.getId()));
            if (outro.isPresent()) {
                throw new RecursoDuplicadoException(
                        profissional.getNome() + " já tem um acesso: " + outro.get().getEmail());
            }
        }
        usuario.setPapel(papel);
        usuario.setProfissional(profissional);
    }

    private Usuario buscar(Clinica clinica, UUID id) {
        return usuarioRepository.findById(id)
                .filter(usuario -> usuario.getClinica() != null
                        && usuario.getClinica().getId().equals(clinica.getId())
                        && usuario.getPapel() != Papel.ADMIN)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private static UsuarioClinicaResponseDTO toDTO(Usuario usuario) {
        Profissional profissional = usuario.getProfissional();
        return new UsuarioClinicaResponseDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPapel(), usuario.isAtivo(),
                profissional != null ? profissional.getId() : null,
                profissional != null ? profissional.getNome() : null);
    }

    private static String gerarSenhaTemporaria() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
