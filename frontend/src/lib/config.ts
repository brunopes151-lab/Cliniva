// Auto-cadastro público de clínica (/cadastro). Desligado por padrão: numa
// instalação de uma clínica só, quem cria clínica e usuários é o
// administrador. Precisa bater com ONBOARDING_PUBLICO_ATIVO do backend.
export const cadastroPublicoAtivo = import.meta.env.VITE_CADASTRO_PUBLICO === 'true'
