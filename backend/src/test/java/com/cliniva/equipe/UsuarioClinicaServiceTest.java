package com.cliniva.equipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.exception.RecursoNaoEncontradoException;
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

@ExtendWith(MockitoExtension.class)
class UsuarioClinicaServiceTest {

    private static final Clinica CLINICA = clinica();

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProfissionalService profissionalService;
    @Mock
    private SupabaseUsersService supabaseUsers;
    @Mock
    private ClinicaContext clinicaContext;

    @InjectMocks
    private UsuarioClinicaService service;

    private static Clinica clinica() {
        Clinica clinica = new Clinica();
        ReflectionTestUtils.setField(clinica, "id", UUID.randomUUID());
        return clinica;
    }

    private Usuario usuario(Papel papel) {
        Usuario usuario = new Usuario();
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        usuario.setClinica(CLINICA);
        usuario.setPapel(papel);
        usuario.setEmail("pessoa@exemplo.test");
        return usuario;
    }

    @Test
    void criaRecepcaoComSenhaTemporariaQuandoSupabaseEstaConfigurado() {
        when(supabaseUsers.configurada()).thenReturn(true);
        when(supabaseUsers.buscarPorEmail("recepcao@exemplo.test")).thenReturn(Optional.empty());
        when(supabaseUsers.criarUsuario(anyString(), anyString()))
                .thenReturn(new UsuarioSupabase("sup-1", "recepcao@exemplo.test"));

        var criado = service.criar(CLINICA, new CriarUsuarioClinicaRequestDTO(
                "Recepção", " Recepcao@Exemplo.test ", Papel.RECEPCAO, UUID.randomUUID()));

        assertThat(criado.usuario().email()).isEqualTo("recepcao@exemplo.test");
        assertThat(criado.usuario().papel()).isEqualTo(Papel.RECEPCAO);
        // Recepção nunca fica ligada a um profissional.
        assertThat(criado.usuario().profissionalId()).isNull();
        assertThat(criado.senhaTemporaria()).hasSize(12);
    }

    @Test
    void semSupabaseCriaSoOAcessoESemSenha() {
        Profissional ana = new Profissional();
        ReflectionTestUtils.setField(ana, "id", UUID.randomUUID());
        ana.setNome("Ana");
        when(profissionalService.buscar(CLINICA, ana.getId())).thenReturn(ana);

        var criado = service.criar(CLINICA, new CriarUsuarioClinicaRequestDTO(
                "Ana", "ana@exemplo.test", Papel.PROFISSIONAL, ana.getId()));

        assertThat(criado.senhaTemporaria()).isNull();
        assertThat(criado.usuario().profissionalNome()).isEqualTo("Ana");
        verify(supabaseUsers, never()).criarUsuario(anyString(), anyString());
    }

    @Test
    void naoCriaAdminDePlataformaPelaClinica() {
        assertThatThrownBy(() -> service.criar(CLINICA, new CriarUsuarioClinicaRequestDTO(
                "X", "x@exemplo.test", Papel.ADMIN, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void administradorNaoTiraOProprioAcesso() {
        Usuario eu = usuario(Papel.OWNER);
        when(usuarioRepository.findById(eu.getId())).thenReturn(Optional.of(eu));
        when(clinicaContext.principalAtual()).thenReturn(new UsuarioPrincipal(eu.getId(), "sub", Papel.OWNER,
                CLINICA, "Eu", eu.getEmail(), null));

        assertThatThrownBy(() -> service.atualizar(CLINICA, eu.getId(),
                new AtualizarUsuarioClinicaRequestDTO("Eu", Papel.RECEPCAO, null, true)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.atualizar(CLINICA, eu.getId(),
                new AtualizarUsuarioClinicaRequestDTO("Eu", Papel.OWNER, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usuarioDeOutraClinicaNaoEEncontrado() {
        Usuario deOutra = usuario(Papel.RECEPCAO);
        deOutra.setClinica(clinica());
        when(usuarioRepository.findById(deOutra.getId())).thenReturn(Optional.of(deOutra));

        assertThatThrownBy(() -> service.atualizar(CLINICA, deOutra.getId(),
                new AtualizarUsuarioClinicaRequestDTO("X", Papel.RECEPCAO, null, true)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(usuarioRepository, never()).save(any());
    }
}
