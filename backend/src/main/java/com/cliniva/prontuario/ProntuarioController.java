package com.cliniva.prontuario;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.prontuario.dtos.ProntuarioDtos.AtivoRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.CorrecaoRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.ModeloFichaDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.NovoRegistroRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.RegistroDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.SalvarModeloRequestDTO;
import com.cliniva.prontuario.dtos.ProntuarioDtos.VersaoDTO;
import com.cliniva.tenancy.ClinicaContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Prontuário e modelos de ficha. Recepção fica de fora já no SecurityConfig;
 * as demais regras (paciente do profissional, quem corrige) ficam no serviço.
 * Não há rota para apagar nada.
 */
@RestController
@RequiredArgsConstructor
public class ProntuarioController {

    private final ProntuarioService service;
    private final ClinicaContext clinicaContext;

    @GetMapping("/api/fichas/modelos")
    public List<ModeloFichaDTO> modelos(@RequestParam(defaultValue = "false") boolean todos) {
        return service.listarModelos(clinicaContext.obterClinicaAtual(), todos);
    }

    @PostMapping("/api/fichas/modelos")
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloFichaDTO criarModelo(@Valid @RequestBody SalvarModeloRequestDTO request) {
        return service.criarModelo(clinicaContext.obterClinicaAtual(), request);
    }

    @PostMapping("/api/fichas/modelos/{familiaId}/versoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ModeloFichaDTO novaVersao(@PathVariable UUID familiaId, @Valid @RequestBody SalvarModeloRequestDTO request) {
        return service.novaVersao(clinicaContext.obterClinicaAtual(), familiaId, request);
    }

    @PatchMapping("/api/fichas/modelos/{familiaId}/ativo")
    public ModeloFichaDTO ativo(@PathVariable UUID familiaId, @RequestBody AtivoRequestDTO request) {
        return service.definirAtivo(clinicaContext.obterClinicaAtual(), familiaId, request.ativo());
    }

    @GetMapping("/api/clientes/{clienteId}/prontuario")
    public List<RegistroDTO> listar(@PathVariable UUID clienteId) {
        return service.listar(clinicaContext.obterClinicaAtual(), clienteId);
    }

    @PostMapping("/api/clientes/{clienteId}/prontuario")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroDTO registrar(@PathVariable UUID clienteId, @Valid @RequestBody NovoRegistroRequestDTO request) {
        return service.registrar(clinicaContext.obterClinicaAtual(), clienteId, request);
    }

    @GetMapping("/api/prontuario/{registroId}/versoes")
    public List<VersaoDTO> historico(@PathVariable UUID registroId) {
        return service.historico(clinicaContext.obterClinicaAtual(), registroId);
    }

    @PostMapping("/api/prontuario/{registroId}/versoes")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroDTO corrigir(@PathVariable UUID registroId, @Valid @RequestBody CorrecaoRequestDTO request) {
        return service.corrigir(clinicaContext.obterClinicaAtual(), registroId, request);
    }
}
