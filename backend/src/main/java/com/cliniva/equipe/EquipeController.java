package com.cliniva.equipe;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.dtos.EquipeDtos.EspecialidadeDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.ProfissionalResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarEspecialidadeRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SalvarProfissionalRequestDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Profissionais e especialidades. Ler é para toda a equipe (os seletores
 * da agenda precisam); criar e editar só o administrador (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class EquipeController {

    private final EquipeService equipeService;
    private final ClinicaContext clinicaContext;

    @GetMapping("/api/profissionais")
    public List<ProfissionalResponseDTO> listarProfissionais() {
        return equipeService.listarProfissionais(clinicaContext.obterClinicaAtual());
    }

    @PostMapping("/api/profissionais")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalResponseDTO criarProfissional(@Valid @RequestBody SalvarProfissionalRequestDTO request) {
        return equipeService.criarProfissional(clinicaContext.obterClinicaAtual(), request);
    }

    @PutMapping("/api/profissionais/{id}")
    public ProfissionalResponseDTO atualizarProfissional(@PathVariable UUID id,
            @Valid @RequestBody SalvarProfissionalRequestDTO request) {
        return equipeService.atualizarProfissional(clinicaContext.obterClinicaAtual(), id, request);
    }

    @GetMapping("/api/especialidades")
    public List<EspecialidadeDTO> listarEspecialidades() {
        return equipeService.listarEspecialidades(clinicaContext.obterClinicaAtual());
    }

    @PostMapping("/api/especialidades")
    @ResponseStatus(HttpStatus.CREATED)
    public EspecialidadeDTO criarEspecialidade(@Valid @RequestBody SalvarEspecialidadeRequestDTO request) {
        return equipeService.criarEspecialidade(clinicaContext.obterClinicaAtual(), request);
    }

    @PutMapping("/api/especialidades/{id}")
    public EspecialidadeDTO renomearEspecialidade(@PathVariable UUID id,
            @Valid @RequestBody SalvarEspecialidadeRequestDTO request) {
        return equipeService.renomearEspecialidade(clinicaContext.obterClinicaAtual(), id, request);
    }

    @DeleteMapping("/api/especialidades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirEspecialidade(@PathVariable UUID id) {
        equipeService.excluirEspecialidade(clinicaContext.obterClinicaAtual(), id);
    }
}
