package com.cliniva.tenancy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cliniva.auth.UsuarioPrincipal;
import com.cliniva.exception.AcessoNaoPermitidoException;
import com.cliniva.exception.RecursoDuplicadoException;
import com.cliniva.exception.RecursoNaoEncontradoException;
import com.cliniva.tenancy.dtos.TenancyDtos.AtualizarMarcaRequestDTO;
import com.cliniva.tenancy.dtos.TenancyDtos.MarcaResponseDTO;

import lombok.RequiredArgsConstructor;

/**
 * Nome e logo da clínica: o único ponto de configuração da marca.
 *
 * <p>Nada de nome fixo no código: as telas leem daqui. Sem clínica
 * identificável (tela de login de uma instalação com várias clínicas), vale
 * o padrão neutro {@link #NOME_PADRAO}.
 */
@Service
@RequiredArgsConstructor
public class ClinicaMarcaService {

    public static final String NOME_PADRAO = "Minha Clínica";

    private final ClinicaContext clinicaContext;
    private final ClinicaRepository clinicaRepository;

    @Transactional(readOnly = true)
    public MarcaResponseDTO marcaAtual() {
        return paraDto(clinicaContext.obterClinicaAtual());
    }

    @Transactional
    public MarcaResponseDTO atualizar(AtualizarMarcaRequestDTO request) {
        exigirResponsavel();
        Clinica clinica = clinicaContext.obterClinicaAtual();

        String nome = request.nome().trim();
        if (clinicaRepository.existsByNomeAndIdNot(nome, clinica.getId())) {
            throw new RecursoDuplicadoException("Já existe uma clínica com esse nome");
        }
        clinica.setNome(nome);
        String logo = request.logoDataUrl();
        clinica.setLogoDataUrl(logo == null || logo.isBlank() ? null : logo);
        return paraDto(clinicaRepository.save(clinica));
    }

    /**
     * Marca para telas sem login. Com slug (agendamento online), a da
     * clínica do link. Sem slug, a da única clínica ativa; se houver mais de
     * uma, o padrão neutro, para não expor o nome de uma clínica na porta de
     * outra.
     */
    @Transactional(readOnly = true)
    public MarcaResponseDTO marcaPublica(String slug) {
        if (slug != null && !slug.isBlank()) {
            return clinicaRepository.findBySlugAndAtivaTrue(slug.trim())
                    .map(this::paraDto)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Clínica não encontrada"));
        }
        List<Clinica> ativas = clinicaRepository.findTop2ByAtivaTrueOrderByCriadaEmAsc();
        return ativas.size() == 1 ? paraDto(ativas.get(0)) : new MarcaResponseDTO(NOME_PADRAO, null);
    }

    private void exigirResponsavel() {
        UsuarioPrincipal principal = clinicaContext.principalAtual();
        if (principal == null || (principal.papel() != Papel.OWNER && principal.papel() != Papel.ADMIN)) {
            throw new AcessoNaoPermitidoException("Só o administrador da clínica altera nome e logo");
        }
    }

    private MarcaResponseDTO paraDto(Clinica clinica) {
        return new MarcaResponseDTO(clinica.getNome(), clinica.getLogoDataUrl());
    }
}
