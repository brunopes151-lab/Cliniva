package com.cliniva.lgpd;

/**
 * Primeira versão do termo, criada quando a clínica ainda não tem nenhum.
 * É um ponto de partida: a clínica revisa o texto (de preferência com um
 * advogado) e publica a versão dela na tela de configurações.
 */
final class TermoPadrao {

    private TermoPadrao() {
    }

    static final String TEXTO = """
            TERMO DE CONSENTIMENTO PARA TRATAMENTO DE DADOS PESSOAIS E DE SAÚDE

            Autorizo a clínica a tratar meus dados pessoais e os dados sobre a minha saúde, nos termos da Lei Geral de Proteção de Dados (Lei 13.709/2018), para as seguintes finalidades:

            1. Realizar meus atendimentos de estética e/ou fisioterapia, incluindo anamnese, avaliação, plano de tratamento e registro da evolução de cada sessão no meu prontuário.
            2. Agendar, confirmar e lembrar minhas sessões pelos contatos que informei.
            3. Cumprir obrigações legais e as normas dos conselhos profissionais, inclusive a guarda do prontuário pelo prazo que elas exigem.

            Meus dados de saúde são acessados apenas pelos profissionais que me atendem e pela administração da clínica, e todo acesso ao prontuário fica registrado. Eles não são vendidos nem usados para propaganda, e só são compartilhados quando a lei exigir ou com a minha autorização.

            Sei que posso, a qualquer momento, pedir acesso, correção ou uma cópia dos meus dados, e revogar este consentimento. A revogação vale dali em diante: o que já foi registrado no prontuário continua guardado pelo prazo legal.
            """.strip();
}
