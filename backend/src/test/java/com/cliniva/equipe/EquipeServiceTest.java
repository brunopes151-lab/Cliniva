package com.cliniva.equipe;

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
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarEspecialidadeRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarProfissionalRequestDTO;

@ExtendWith(MockitoExtension.class)
class EquipeServiceTest {

    private static final Clinica CLINICA = new Clinica();

    static {
        ReflectionTestUtils.setField(CLINICA, "id", UUID.randomUUID());
    }

    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private EspecialidadeRepository especialidadeRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private ProfissionalService profissionalService;
    @Mock
    private AgendaService agendaService;

    @InjectMocks
    private EquipeService equipeService;

    private static Especialidade especialidade(String nome) {
        Especialidade especialidade = new Especialidade();
        ReflectionTestUtils.setField(especialidade, "id", UUID.randomUUID());
        especialidade.setClinica(CLINICA);
        especialidade.setNome(nome);
        return especialidade;
    }

    private static Profissional profissional(String nome) {
        Profissional profissional = new Profissional();
        ReflectionTestUtils.setField(profissional, "id", UUID.randomUUID());
        profissional.setClinica(CLINICA);
        profissional.setNome(nome);
        return profissional;
    }

    @Test
    void criaProfissionalComEspecialidadesServicosEExpedientePadrao() {
        Especialidade fisio = especialidade("Fisioterapia");
        Servico pilates = new Servico();
        ReflectionTestUtils.setField(pilates, "id", UUID.randomUUID());
        when(especialidadeRepository.findByIdAndClinica_Id(fisio.getId(), CLINICA.getId()))
                .thenReturn(Optional.of(fisio));
        when(servicoRepository.findByIdAndClinica(pilates.getId(), CLINICA)).thenReturn(Optional.of(pilates));
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(i -> i.getArgument(0));

        var criado = equipeService.criarProfissional(CLINICA, new SalvarProfissionalRequestDTO(
                "  Ana Souza ", "#A1B2C3", null, List.of(fisio.getId()), List.of(pilates.getId())));

        assertThat(criado.nome()).isEqualTo("Ana Souza");
        assertThat(criado.ativo()).isTrue();
        assertThat(criado.especialidades()).extracting(e -> e.nome()).containsExactly("Fisioterapia");
        assertThat(criado.servicoIds()).containsExactly(pilates.getId());
        verify(agendaService).copiarExpedienteGeral(any(), any(Profissional.class));
    }

    @Test
    void naoAceitaNomeRepetidoNaClinica() {
        when(profissionalRepository.existsByClinica_IdAndNomeIgnoreCase(CLINICA.getId(), "Ana")).thenReturn(true);

        assertThatThrownBy(() -> equipeService.criarProfissional(CLINICA,
                new SalvarProfissionalRequestDTO("Ana", null, true, null, null)))
                .isInstanceOf(RecursoDuplicadoException.class);
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void naoAceitaEspecialidadeDeOutraClinica() {
        UUID deOutraClinica = UUID.randomUUID();
        when(especialidadeRepository.findByIdAndClinica_Id(deOutraClinica, CLINICA.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipeService.criarProfissional(CLINICA,
                new SalvarProfissionalRequestDTO("Ana", null, true, List.of(deOutraClinica), null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void geralNaoPodeSerRenomeadoMasPodeSerDesativado() {
        Profissional geral = profissional(ProfissionalService.NOME_GERAL);
        when(profissionalService.buscar(CLINICA, geral.getId())).thenReturn(geral);
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> equipeService.atualizarProfissional(CLINICA, geral.getId(),
                new SalvarProfissionalRequestDTO("Recepção", null, true, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        var atualizado = equipeService.atualizarProfissional(CLINICA, geral.getId(),
                new SalvarProfissionalRequestDTO("Geral", null, false, null, null));
        assertThat(atualizado.ativo()).isFalse();
        assertThat(atualizado.geral()).isTrue();
    }

    @Test
    void excluirEspecialidadeTiraDosProfissionais() {
        Especialidade estetica = especialidade("Estética");
        Profissional ana = profissional("Ana");
        ana.getEspecialidades().add(estetica);
        when(especialidadeRepository.findByIdAndClinica_Id(estetica.getId(), CLINICA.getId()))
                .thenReturn(Optional.of(estetica));
        when(profissionalRepository.findByClinica_IdOrderByNomeAsc(CLINICA.getId())).thenReturn(List.of(ana));

        equipeService.excluirEspecialidade(CLINICA, estetica.getId());

        assertThat(ana.getEspecialidades()).isEmpty();
        verify(especialidadeRepository).delete(estetica);
    }

    @Test
    void especialidadeRepetidaERecusada() {
        when(especialidadeRepository.existsByClinica_IdAndNomeIgnoreCase(CLINICA.getId(), "Estética"))
                .thenReturn(true);

        assertThatThrownBy(() -> equipeService.criarEspecialidade(CLINICA,
                new SalvarEspecialidadeRequestDTO(" Estética ")))
                .isInstanceOf(RecursoDuplicadoException.class);
    }
}
