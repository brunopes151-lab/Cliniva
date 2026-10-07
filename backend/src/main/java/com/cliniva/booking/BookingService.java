package com.cliniva.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.AgendaService;
import com.cliniva.agenda.dtos.DisponibilidadeDiaDTO;
import com.cliniva.atendimento.AtendimentoService;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoRequestDTO.ServicoSelecionadoDTO;
import com.cliniva.atendimento.dtos.CreateAtendimentoResponseDTO;
import com.cliniva.booking.dtos.BookingRequestDTO;
import com.cliniva.booking.dtos.BookingResponseDTO;
import com.cliniva.booking.dtos.ServicoPublicoDTO;
import com.cliniva.cliente.Cliente;
import com.cliniva.cliente.ClienteRepository;
import com.cliniva.cliente.enums.ClienteStatus;
import com.cliniva.cliente.enums.OrigemCliente;
import com.cliniva.common.Normalizador;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.servico.Servico;
import com.cliniva.servico.ServicoRepository;
import com.cliniva.tenancy.Clinica;
import com.cliniva.tenancy.ClinicaRepository;
import com.cliniva.tenancy.Especialidade;
import com.cliniva.tenancy.Profissional;
import com.cliniva.tenancy.ProfissionalService;
import com.cliniva.tenancy.dtos.EquipeDtos.ProfissionalPublicoDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final ClinicaRepository clinicaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;
    private final AgendaService agendaService;
    private final AtendimentoService atendimentoService;
    private final ProfissionalService profissionalService;

    @Transactional(readOnly = true)
    public List<ServicoPublicoDTO> listarServicos(String slug) {
        Clinica clinica = buscarClinicaAtiva(slug);
        return servicoRepository.findByClinica(clinica).stream()
                .map(servico -> new ServicoPublicoDTO(servico.getId(), servico.getNome(),
                        servico.getDescricao(), servico.getValor(), servico.getDuracaoMinutos(),
                        clinica.getNome()))
                .toList();
    }

    /** Quem pode fazer o serviço, para o paciente escolher. Só nome e especialidades. */
    @Transactional(readOnly = true)
    public List<ProfissionalPublicoDTO> listarProfissionais(String slug, UUID servicoId) {
        Clinica clinica = buscarClinicaAtiva(slug);
        Servico servico = servicoRepository.findByIdAndClinica(servicoId, clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
        return profissionalService.aptos(clinica, List.of(servico)).stream()
                .map(profissional -> new ProfissionalPublicoDTO(profissional.getId(), profissional.getNome(),
                        profissional.getEspecialidades().stream().map(Especialidade::getNome).sorted().toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DisponibilidadeDiaDTO disponibilidade(String slug, LocalDate data, UUID servicoId, UUID profissionalId) {
        Clinica clinica = buscarClinicaAtiva(slug);
        // Janela/passado são validados dentro de AgendaService (regra única).
        return agendaService.disponibilidadeDia(clinica, data, servicoId, profissionalId);
    }

    @Transactional
    public BookingResponseDTO agendar(String slug, BookingRequestDTO request) {
        Clinica clinica = buscarClinicaAtiva(slug);
        Servico servico = servicoRepository.findByIdAndClinica(request.servicoId(), clinica)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        // Validação real (janela, expediente, passado e conflito) acontece em
        // createAtendimento, já sob lock pessimista da clínica.
        UUID profissionalId = request.profissionalId();
        if (profissionalId == null) {
            // Sem preferência: o primeiro profissional livre, já sob o lock.
            Profissional livre = agendaService.escolherLivre(clinica, List.of(servico), request.dataHora(),
                    servico.getDuracaoMinutos());
            profissionalId = livre.getId();
        }

        Cliente cliente = localizarOuCriarCliente(clinica, request);

        CreateAtendimentoRequestDTO atendimentoRequest = new CreateAtendimentoRequestDTO(
                cliente.getId(),
                request.dataHora(),
                List.of(new ServicoSelecionadoDTO(request.servicoId(), List.of())),
                profissionalId);
        CreateAtendimentoResponseDTO criado = atendimentoService.createAtendimento(clinica, atendimentoRequest);

        return new BookingResponseDTO(
                criado.id(),
                criado.dataAtendimento(),
                criado.duracaoMinutos(),
                servico.getNome(),
                clinica.getNome(),
                cliente.getNome(),
                criado.profissionalNome());
    }

    private Cliente localizarOuCriarCliente(Clinica clinica, BookingRequestDTO request) {
        String telefone = telefoneNormalizado(request.telefone());
        return clienteRepository.findByTelefoneAndClinica(telefone, clinica)
                .orElseGet(() -> {
                    Cliente novo = new Cliente();
                    novo.setClinica(clinica);
                    novo.setNome(request.nome().trim());
                    novo.setEmail(emailNormalizado(request.email()));
                    novo.setTelefone(telefone);
                    novo.setOrigem(OrigemCliente.ONLINE);
                    novo.setStatus(ClienteStatus.PROSPECT);
                    return clienteRepository.save(novo);
                });
    }

    /** Só dígitos, com DDI 55 para números brasileiros sem prefixo. */
    static String telefoneNormalizado(String telefone) {
        return Normalizador.telefone(telefone);
    }

    private String emailNormalizado(String email) {
        return Normalizador.email(email);
    }

    private Clinica buscarClinicaAtiva(String slug) {
        return clinicaRepository.findBySlugAndAtivaTrue(slug)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Clínica não encontrada"));
    }
}
