import type { Papel } from '@/types'

interface ComPapel {
  papel: Papel
}

/** Administrador da clínica (ou o suporte da plataforma): configura tudo. */
export function ehAdministracao(usuario: ComPapel | null | undefined): boolean {
  return usuario?.papel === 'OWNER' || usuario?.papel === 'ADMIN'
}

/** Administrador ou recepção: cadastra pacientes, estoque e vê o painel. */
export function ehEquipeAdministrativa(usuario: ComPapel | null | undefined): boolean {
  return ehAdministracao(usuario) || usuario?.papel === 'RECEPCAO'
}

/** Login de profissional: só a própria agenda e os próprios pacientes. */
export function ehProfissional(usuario: ComPapel | null | undefined): boolean {
  return usuario?.papel === 'PROFISSIONAL'
}

/** Primeira tela depois do login. */
export function telaInicial(usuario: ComPapel | null | undefined): string {
  return ehEquipeAdministrativa(usuario) ? '/dashboard' : '/agenda'
}

/** Prontuário: administração e profissionais. A recepção não vê conteúdo clínico. */
export function veProntuario(usuario: ComPapel | null | undefined): boolean {
  return !!usuario && usuario.papel !== 'RECEPCAO'
}
