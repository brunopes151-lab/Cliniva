package com.cliniva.prontuario;

import java.util.List;

/**
 * Uma pergunta do modelo. {@code id} é a chave da resposta no conteúdo do
 * registro; {@code opcoes} só vale para OPCOES.
 */
public record CampoFicha(String id, String rotulo, TipoCampo tipo, boolean obrigatorio, List<String> opcoes) {

    public static CampoFicha texto(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.TEXTO, false, null);
    }

    public static CampoFicha longo(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.TEXTO_LONGO, false, null);
    }

    public static CampoFicha obrigatorio(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.TEXTO_LONGO, true, null);
    }

    public static CampoFicha simNao(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.SIM_NAO, false, null);
    }

    public static CampoFicha escala(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.ESCALA, false, null);
    }

    public static CampoFicha numero(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.NUMERO, false, null);
    }

    public static CampoFicha data(String id, String rotulo) {
        return new CampoFicha(id, rotulo, TipoCampo.DATA, false, null);
    }

    public static CampoFicha opcoes(String id, String rotulo, String... opcoes) {
        return new CampoFicha(id, rotulo, TipoCampo.OPCOES, false, List.of(opcoes));
    }
}
