package com.cliniva.tenancy;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.agenda.AgendaService;
import com.cliniva.exception.RecursoDuplicadoException;

import lombok.RequiredArgsConstructor;

/**
 * Criação de clínica usada por <b>todos</b> os caminhos (onboarding público e
 * painel admin). Garante invariantes que a feature de agenda exige: slug
 * público único, expediente padrão semeado e as especialidades da clínica
 * (Estética e Fisioterapia) já cadastradas.
 */
@Service
@RequiredArgsConstructor
public class ClinicaProvisioningService {

    private final ClinicaRepository clinicaRepository;
    private final AgendaService agendaService;
    private final EspecialidadeRepository especialidadeRepository;

    /** Mesmas da migration 20261007000002 para as clínicas que já existiam. */
    static final List<String> ESPECIALIDADES_PADRAO = List.of("Estética", "Fisioterapia");

    @Transactional
    public Clinica criarClinica(String nomeBruto) {
        String nome = nomeBruto.trim();
        if (clinicaRepository.existsByNome(nome)) {
            throw new RecursoDuplicadoException("Clínica com esse nome já cadastrada");
        }

        Clinica clinica = new Clinica();
        clinica.setNome(nome);
        clinica.setSlug(gerarSlugUnico(nome));
        clinicaRepository.save(clinica);
        agendaService.semearPadrao(clinica);
        for (String nomeEspecialidade : ESPECIALIDADES_PADRAO) {
            Especialidade especialidade = new Especialidade();
            especialidade.setClinica(clinica);
            especialidade.setNome(nomeEspecialidade);
            especialidadeRepository.save(especialidade);
        }
        return clinica;
    }

    private String gerarSlugUnico(String nome) {
        String base = slugify(nome);
        String slug = base;
        int sufixo = 2;
        while (clinicaRepository.existsBySlug(slug)) {
            slug = base + "-" + sufixo++;
        }
        return slug;
    }

    static String slugify(String nome) {
        String semAcentos = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = semAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "clinica" : slug;
    }
}
