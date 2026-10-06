package com.cliniva.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.tenancy.dtos.TenancyDtos.AtualizarMarcaRequestDTO;

@ExtendWith(MockitoExtension.class)
class ClinicaMarcaServiceTest {

    private static final String LOGO = "data:image/png;base64,iVBORw0KGgo=";

    @Mock
    private ClinicaContext clinicaContext;
    @Mock
    private ClinicaRepository clinicaRepository;

    @InjectMocks
    private ClinicaMarcaService service;

    private static Clinica clinica(String nome) {
        Clinica clinica = new Clinica();
        ReflectionTestUtils.setField(clinica, "id", UUID.randomUUID());
        clinica.setNome(nome);
        clinica.setSlug("slug");
        return clinica;
    }

    private static UsuarioPrincipal principal(Papel papel, Clinica clinica) {
        return new UsuarioPrincipal(UUID.randomUUID(), "sub", papel, clinica, "Pessoa", "p@exemplo.test");
    }

    @Test
    void responsavelAtualizaNomeELogo() {
        Clinica atual = clinica("Minha Clínica");
        when(clinicaContext.principalAtual()).thenReturn(principal(Papel.OWNER, atual));
        when(clinicaContext.obterClinicaAtual()).thenReturn(atual);
        when(clinicaRepository.save(any(Clinica.class))).thenAnswer(i -> i.getArgument(0));

        var marca = service.atualizar(new AtualizarMarcaRequestDTO("  Clínica Bem-Estar  ", LOGO));

        assertThat(marca.nome()).isEqualTo("Clínica Bem-Estar");
        assertThat(marca.logoDataUrl()).isEqualTo(LOGO);
        assertThat(atual.getNome()).isEqualTo("Clínica Bem-Estar");
    }

    @Test
    void logoVaziaRemoveALogo() {
        Clinica atual = clinica("Minha Clínica");
        atual.setLogoDataUrl(LOGO);
        when(clinicaContext.principalAtual()).thenReturn(principal(Papel.OWNER, atual));
        when(clinicaContext.obterClinicaAtual()).thenReturn(atual);
        when(clinicaRepository.save(any(Clinica.class))).thenAnswer(i -> i.getArgument(0));

        var marca = service.atualizar(new AtualizarMarcaRequestDTO("Minha Clínica", "  "));

        assertThat(marca.logoDataUrl()).isNull();
    }

    @Test
    void nomeDeOutraClinicaDaConflito() {
        Clinica atual = clinica("Minha Clínica");
        when(clinicaContext.principalAtual()).thenReturn(principal(Papel.OWNER, atual));
        when(clinicaContext.obterClinicaAtual()).thenReturn(atual);
        when(clinicaRepository.existsByNomeAndIdNot("Outra", atual.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.atualizar(new AtualizarMarcaRequestDTO("Outra", null)))
                .isInstanceOf(RecursoDuplicadoException.class);
        verify(clinicaRepository, never()).save(any());
    }

    @Test
    void semUsuarioNaoAltera() {
        when(clinicaContext.principalAtual()).thenReturn(null);

        assertThatThrownBy(() -> service.atualizar(new AtualizarMarcaRequestDTO("X", null)))
                .isInstanceOf(AcessoNaoPermitidoException.class);
        verify(clinicaRepository, never()).save(any());
    }

    @Test
    void marcaPublicaPorSlug() {
        Clinica ativa = clinica("Clínica do Link");
        when(clinicaRepository.findBySlugAndAtivaTrue("link")).thenReturn(Optional.of(ativa));

        assertThat(service.marcaPublica(" link ").nome()).isEqualTo("Clínica do Link");
    }

    @Test
    void marcaPublicaPorSlugInexistenteDa404() {
        when(clinicaRepository.findBySlugAndAtivaTrue("nada")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcaPublica("nada"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void marcaPublicaSemSlugUsaAUnicaClinicaAtiva() {
        when(clinicaRepository.findTop2ByAtivaTrueOrderByCriadaEmAsc()).thenReturn(List.of(clinica("Única")));

        assertThat(service.marcaPublica(null).nome()).isEqualTo("Única");
    }

    @Test
    void marcaPublicaSemSlugComVariasClinicasUsaPadraoNeutro() {
        when(clinicaRepository.findTop2ByAtivaTrueOrderByCriadaEmAsc())
                .thenReturn(List.of(clinica("A"), clinica("B")));

        var marca = service.marcaPublica(null);

        assertThat(marca.nome()).isEqualTo(ClinicaMarcaService.NOME_PADRAO);
        assertThat(marca.logoDataUrl()).isNull();
    }
}
