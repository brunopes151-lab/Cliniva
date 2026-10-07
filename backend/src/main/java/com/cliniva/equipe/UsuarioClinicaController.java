package com.cliniva.equipe;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.tenancy.ClinicaContext;
import com.cliniva.tenancy.dtos.EquipeDtos.AtualizarUsuarioClinicaRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.CriarUsuarioClinicaRequestDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.SenhaTemporariaResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.UsuarioClinicaResponseDTO;
import com.cliniva.tenancy.dtos.EquipeDtos.UsuarioCriadoResponseDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Usuários e acessos da clínica. Só administrador (SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioClinicaController {

    private final UsuarioClinicaService usuarioClinicaService;
    private final ClinicaContext clinicaContext;

    @GetMapping
    public List<UsuarioClinicaResponseDTO> listar() {
        return usuarioClinicaService.listar(clinicaContext.obterClinicaAtual());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioCriadoResponseDTO criar(@Valid @RequestBody CriarUsuarioClinicaRequestDTO request) {
        return usuarioClinicaService.criar(clinicaContext.obterClinicaAtual(), request);
    }

    @PutMapping("/{id}")
    public UsuarioClinicaResponseDTO atualizar(@PathVariable UUID id,
            @Valid @RequestBody AtualizarUsuarioClinicaRequestDTO request) {
        return usuarioClinicaService.atualizar(clinicaContext.obterClinicaAtual(), id, request);
    }

    @PostMapping("/{id}/reset-senha")
    public SenhaTemporariaResponseDTO resetarSenha(@PathVariable UUID id) {
        return usuarioClinicaService.resetarSenha(clinicaContext.obterClinicaAtual(), id);
    }
}
