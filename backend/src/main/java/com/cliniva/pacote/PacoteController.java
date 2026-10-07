package com.cliniva.pacote;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.pacote.dtos.PacoteDtos.PacoteClienteResponseDTO;
import com.cliniva.pacote.dtos.PacoteDtos.PacoteResponseDTO;
import com.cliniva.pacote.dtos.PacoteDtos.SalvarPacoteRequestDTO;
import com.cliniva.pacote.dtos.PacoteDtos.VenderPacoteRequestDTO;
import com.cliniva.tenancy.ClinicaContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Modelos: leitura para toda a equipe, escrita só da administração.
 * Pacotes do paciente: leitura para quem vê o paciente, venda e cancelamento
 * pela recepção e administração (regras em SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class PacoteController {

    private final PacoteService pacoteService;
    private final ClinicaContext clinicaContext;

    @GetMapping("/api/pacotes")
    public List<PacoteResponseDTO> listarModelos() {
        return pacoteService.listarModelos(clinicaContext.obterClinicaAtual());
    }

    @PostMapping("/api/pacotes")
    @ResponseStatus(HttpStatus.CREATED)
    public PacoteResponseDTO criarModelo(@Valid @RequestBody SalvarPacoteRequestDTO request) {
        return pacoteService.criarModelo(clinicaContext.obterClinicaAtual(), request);
    }

    @PutMapping("/api/pacotes/{id}")
    public PacoteResponseDTO atualizarModelo(@PathVariable UUID id, @Valid @RequestBody SalvarPacoteRequestDTO request) {
        return pacoteService.atualizarModelo(clinicaContext.obterClinicaAtual(), id, request);
    }

    @GetMapping("/api/clientes/{clienteId}/pacotes")
    public List<PacoteClienteResponseDTO> listarDoCliente(@PathVariable UUID clienteId) {
        return pacoteService.listarDoCliente(clinicaContext.obterClinicaAtual(), clienteId);
    }

    @PostMapping("/api/clientes/{clienteId}/pacotes")
    @ResponseStatus(HttpStatus.CREATED)
    public PacoteClienteResponseDTO vender(@PathVariable UUID clienteId,
            @Valid @RequestBody VenderPacoteRequestDTO request) {
        return pacoteService.vender(clinicaContext.obterClinicaAtual(), clienteId, request);
    }

    @PostMapping("/api/clientes/{clienteId}/pacotes/{id}/cancelar")
    public PacoteClienteResponseDTO cancelar(@PathVariable UUID clienteId, @PathVariable UUID id) {
        return pacoteService.cancelar(clinicaContext.obterClinicaAtual(), clienteId, id);
    }
}
