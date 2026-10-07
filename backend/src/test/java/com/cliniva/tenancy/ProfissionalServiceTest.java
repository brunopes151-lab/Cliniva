package com.cliniva.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cliniva.servico.Servico;

@ExtendWith(MockitoExtension.class)
class ProfissionalServiceTest {

    private static final Clinica CLINICA = new Clinica();

    static {
        ReflectionTestUtils.setField(CLINICA, "id", UUID.randomUUID());
    }

    @Mock
    private ProfissionalRepository profissionalRepository;

    @InjectMocks
    private ProfissionalService profissionalService;

    private static Profissional profissional(String nome, boolean ativo) {
        Profissional profissional = new Profissional();
        ReflectionTestUtils.setField(profissional, "id", UUID.randomUUID());
        profissional.setClinica(CLINICA);
        profissional.setNome(nome);
        profissional.setAtivo(ativo);
        return profissional;
    }

    private static Servico servico(String nome) {
        Servico servico = new Servico();
        ReflectionTestUtils.setField(servico, "id", UUID.randomUUID());
        servico.setNome(nome);
        return servico;
    }

    @Test
    void servicoSemVinculoPodeSerFeitoPorQualquerProfissionalAtivo() {
        Profissional ana = profissional("Ana", true);
        Profissional bia = profissional("Bia", true);
        Servico limpeza = servico("Limpeza");
        when(profissionalRepository.findByClinica_IdAndAtivoTrueOrderByNomeAsc(CLINICA.getId()))
                .thenReturn(List.of(ana, bia));
        when(profissionalRepository.idsVinculadosAoServico(limpeza.getId())).thenReturn(List.of());

        assertThat(profissionalService.aptos(CLINICA, List.of(limpeza))).containsExactly(ana, bia);
    }

    @Test
    void servicoComVinculoSoAceitaQuemEstaVinculado() {
        Profissional ana = profissional("Ana", true);
        Profissional bia = profissional("Bia", true);
        Servico pilates = servico("Pilates");
        when(profissionalRepository.findByClinica_IdAndAtivoTrueOrderByNomeAsc(CLINICA.getId()))
                .thenReturn(List.of(ana, bia));
        when(profissionalRepository.idsVinculadosAoServico(pilates.getId())).thenReturn(List.of(bia.getId()));

        assertThat(profissionalService.aptos(CLINICA, List.of(pilates))).containsExactly(bia);
        assertThatThrownBy(() -> profissionalService.exigirApto(ana, List.of(pilates)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ana não realiza o serviço Pilates");
    }

    @Test
    void profissionalInativoNaoRecebeAgendamento() {
        Profissional inativa = profissional("Carla", false);

        assertThatThrownBy(() -> profissionalService.exigirApto(inativa, List.of(servico("Limpeza"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inativo");
    }
}
