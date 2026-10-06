package com.cliniva.tenancy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cliniva.tenancy.dtos.TenancyDtos.CadastroOnboardingRequestDTO;
import com.cliniva.tenancy.dtos.TenancyDtos.CadastroOnboardingResponseDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Auto-cadastro público de clínica.
 *
 * <p>Desligado por padrão ({@code ONBOARDING_PUBLICO_ATIVO=false}): numa
 * instalação de uma clínica só, ninguém de fora deve conseguir criar
 * clínica. Desligado, a rota nem é registrada e responde 404. Clínicas e
 * responsáveis passam a ser criados pelo painel do administrador.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cliniva.onboarding-publico.ativo", havingValue = "true")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/api/public/onboarding")
    @ResponseStatus(HttpStatus.CREATED)
    public CadastroOnboardingResponseDTO cadastrarClinica(
            @Valid @RequestBody CadastroOnboardingRequestDTO request) {
        return onboardingService.cadastrarClinica(request);
    }
}
