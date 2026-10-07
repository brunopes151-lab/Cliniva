import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { ehAdministracao, ehEquipeAdministrativa, telaInicial } from '@/lib/perfis'

/** Telas de configuração da clínica: só o administrador (ou o ADMIN em suporte). */
export function RequerResponsavel({ children }: { children: ReactNode }) {
  const { usuario } = useAuth()
  if (usuario && !ehAdministracao(usuario)) return <Navigate to={telaInicial(usuario)} replace />
  return children
}

/** Telas da administração e da recepção (painel, estoque); o profissional não vê. */
export function RequerEquipeAdministrativa({ children }: { children: ReactNode }) {
  const { usuario } = useAuth()
  if (usuario && !ehEquipeAdministrativa(usuario)) return <Navigate to={telaInicial(usuario)} replace />
  return children
}
