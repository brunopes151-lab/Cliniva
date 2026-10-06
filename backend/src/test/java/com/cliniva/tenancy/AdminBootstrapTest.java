package com.cliniva.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Test
    void semEmailConfiguradoNaoFazNada() {
        new AdminBootstrap(usuarioRepository, "  ").run(null);

        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void criaAdminAtivoComEmailNormalizado() {
        when(usuarioRepository.findByEmailIgnoreCase("admin@exemplo.test")).thenReturn(Optional.empty());

        new AdminBootstrap(usuarioRepository, " Admin@Exemplo.test ").run(null);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@exemplo.test");
        assertThat(admin.getPapel()).isEqualTo(Papel.ADMIN);
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.getClinica()).isNull();
    }

    @Test
    void naoDuplicaAdminExistente() {
        Usuario existente = new Usuario();
        existente.setPapel(Papel.ADMIN);
        when(usuarioRepository.findByEmailIgnoreCase("admin@exemplo.test")).thenReturn(Optional.of(existente));

        new AdminBootstrap(usuarioRepository, "admin@exemplo.test").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void nuncaPromoveUsuarioDeClinica() {
        Usuario dona = new Usuario();
        dona.setPapel(Papel.OWNER);
        when(usuarioRepository.findByEmailIgnoreCase("dona@exemplo.test")).thenReturn(Optional.of(dona));

        new AdminBootstrap(usuarioRepository, "dona@exemplo.test").run(null);

        verify(usuarioRepository, never()).save(any());
        assertThat(dona.getPapel()).isEqualTo(Papel.OWNER);
    }
}
