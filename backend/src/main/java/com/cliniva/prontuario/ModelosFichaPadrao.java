package com.cliniva.prontuario;

import static com.cliniva.prontuario.CampoFicha.data;
import static com.cliniva.prontuario.CampoFicha.escala;
import static com.cliniva.prontuario.CampoFicha.longo;
import static com.cliniva.prontuario.CampoFicha.numero;
import static com.cliniva.prontuario.CampoFicha.obrigatorio;
import static com.cliniva.prontuario.CampoFicha.opcoes;
import static com.cliniva.prontuario.CampoFicha.simNao;
import static com.cliniva.prontuario.CampoFicha.texto;

import java.util.List;

/**
 * Formulários com que toda clínica começa. São só um ponto de partida: o
 * administrador edita (gerando versão nova) ou desativa.
 */
final class ModelosFichaPadrao {

    record Padrao(String nome, AreaFicha area, TipoRegistro tipo, List<CampoFicha> campos) {
    }

    private ModelosFichaPadrao() {
    }

    static final List<Padrao> TODOS = List.of(
            new Padrao("Anamnese estética", AreaFicha.ESTETICA, TipoRegistro.ANAMNESE, List.of(
                    obrigatorio("queixa", "Queixa principal"),
                    longo("objetivo", "Objetivo do tratamento"),
                    longo("tratamentos_anteriores", "Tratamentos estéticos anteriores"),
                    texto("alergias", "Alergias"),
                    texto("medicamentos", "Medicamentos em uso"),
                    simNao("gestante", "Gestante ou amamentando"),
                    simNao("acidos", "Usa ácidos ou retinoides"),
                    opcoes("exposicao_solar", "Exposição ao sol", "Baixa", "Moderada", "Alta"),
                    opcoes("fototipo", "Fototipo (Fitzpatrick)", "I", "II", "III", "IV", "V", "VI"),
                    simNao("tabagismo", "Fuma"),
                    longo("condicoes", "Doenças, condições de pele ou contraindicações"),
                    longo("observacoes", "Observações"))),
            new Padrao("Anamnese fisioterapêutica", AreaFicha.FISIOTERAPIA, TipoRegistro.ANAMNESE, List.of(
                    obrigatorio("queixa", "Queixa principal"),
                    longo("hda", "História da doença atual"),
                    data("inicio_sintomas", "Início dos sintomas"),
                    escala("dor", "Dor (0 a 10)"),
                    texto("local_dor", "Local da dor"),
                    longo("piora_melhora", "O que piora e o que melhora"),
                    texto("diagnostico_medico", "Diagnóstico médico"),
                    longo("cirurgias", "Cirurgias e lesões anteriores"),
                    texto("medicamentos", "Medicamentos em uso"),
                    longo("exames", "Exames trazidos"),
                    texto("atividade_fisica", "Atividade física"),
                    longo("observacoes", "Observações"))),
            new Padrao("Avaliação fisioterapêutica", AreaFicha.FISIOTERAPIA, TipoRegistro.AVALIACAO_INICIAL, List.of(
                    longo("inspecao", "Inspeção e postura"),
                    longo("palpacao", "Palpação"),
                    longo("adm", "Amplitude de movimento"),
                    longo("forca", "Força muscular"),
                    longo("testes", "Testes especiais"),
                    escala("dor", "Dor (0 a 10)"),
                    obrigatorio("diagnostico", "Diagnóstico fisioterapêutico"))),
            new Padrao("Plano terapêutico", AreaFicha.GERAL, TipoRegistro.PLANO_TERAPEUTICO, List.of(
                    obrigatorio("objetivos", "Objetivos"),
                    longo("condutas", "Condutas e procedimentos"),
                    texto("frequencia", "Frequência sugerida"),
                    numero("sessoes", "Sessões previstas"),
                    longo("orientacoes", "Orientações ao paciente"))),
            new Padrao("Evolução da sessão", AreaFicha.GERAL, TipoRegistro.EVOLUCAO, List.of(
                    obrigatorio("descricao", "O que foi feito na sessão"),
                    escala("dor", "Dor (0 a 10)"),
                    longo("resposta", "Resposta do paciente e intercorrências"),
                    longo("orientacoes", "Orientações"))),
            new Padrao("Reavaliação", AreaFicha.GERAL, TipoRegistro.REAVALIACAO, List.of(
                    obrigatorio("evolucao", "Evolução desde a última avaliação"),
                    escala("dor", "Dor (0 a 10)"),
                    longo("metas", "Metas atingidas"),
                    longo("ajustes", "Ajustes no plano"))));
}
