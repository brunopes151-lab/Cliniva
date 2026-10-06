package com.cliniva.tenancy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.tenancy.dtos.TenancyDtos.AtualizarMarcaRequestDTO;
import com.cliniva.tenancy.dtos.TenancyDtos.MarcaResponseDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ClinicaMarcaController {

    private final ClinicaMarcaService marcaService;

    @GetMapping("/api/clinica/marca")
    public MarcaResponseDTO marca() {
        return marcaService.marcaAtual();
    }

    @PutMapping("/api/clinica/marca")
    public MarcaResponseDTO atualizar(@Valid @RequestBody AtualizarMarcaRequestDTO request) {
        return marcaService.atualizar(request);
    }

    @GetMapping("/api/public/marca")
    public MarcaResponseDTO marcaPublica(@RequestParam(required = false) String slug) {
        return marcaService.marcaPublica(slug);
    }
}
