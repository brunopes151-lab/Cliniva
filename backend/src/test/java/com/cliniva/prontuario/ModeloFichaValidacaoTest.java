package com.cliniva.prontuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModeloFichaValidacaoTest {

    @Test
    void modelosPadraoSaoValidos() {
        for (ModelosFichaPadrao.Padrao padrao : ModelosFichaPadrao.TODOS) {
            assertThat(ProntuarioService.validarCampos(padrao.campos())).hasSameSizeAs(padrao.campos());
            assertThat(padrao.campos()).anyMatch(CampoFicha::obrigatorio);
        }
    }

    @Test
    void recusaIdentificadorRepetidoOuInvalido() {
        assertThatThrownBy(() -> ProntuarioService.validarCampos(List.of(
                CampoFicha.texto("queixa", "A"), CampoFicha.texto("queixa", "B"))))
                .hasMessageContaining("repetida");
        assertThatThrownBy(() -> ProntuarioService.validarCampos(List.of(CampoFicha.texto("Queixa Principal", "A"))))
                .hasMessageContaining("Identificador");
    }

    @Test
    void opcoesPrecisamDeDuasEscolhas() {
        assertThatThrownBy(() -> ProntuarioService.validarCampos(List.of(
                CampoFicha.opcoes("lado", "Lado", "Direito"))))
                .hasMessageContaining("duas opções");
        assertThat(ProntuarioService.validarCampos(List.of(
                CampoFicha.opcoes("lado", " Lado ", " Direito", "Esquerdo", "Esquerdo"))).get(0))
                .satisfies(c -> {
                    assertThat(c.rotulo()).isEqualTo("Lado");
                    assertThat(c.opcoes()).containsExactly("Direito", "Esquerdo");
                });
    }

    @Test
    void conteudoTiraEspacosEDescartaVazios() {
        ModeloFicha modelo = new ModeloFicha();
        modelo.setCampos(List.of(CampoFicha.obrigatorio("queixa", "Queixa"), CampoFicha.simNao("gestante", "Gestante"),
                CampoFicha.numero("sessoes", "Sessões")));
        var conteudo = new java.util.HashMap<String, Object>();
        conteudo.put("queixa", "  Manchas no rosto ");
        conteudo.put("gestante", false);
        conteudo.put("sessoes", null);
        assertThat(ProntuarioService.validarConteudo(modelo, conteudo))
                .containsOnlyKeys("queixa", "gestante")
                .containsEntry("queixa", "Manchas no rosto")
                .containsEntry("gestante", false);
        assertThatThrownBy(() -> ProntuarioService.validarConteudo(modelo, java.util.Map.of("queixa", "x",
                "gestante", "sim"))).hasMessageContaining("Gestante");
    }
}
