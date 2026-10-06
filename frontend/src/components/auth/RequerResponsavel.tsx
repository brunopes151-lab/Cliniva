import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

/** Telas de configuração da clínica: só o responsável (ou o ADMIN em suporte). */
export function RequerResponsavel({ children }: { children: ReactNode }) {
  const { usuario } = useAuth()
  if (usuario && usuario.papel !== 'OWNER' && usuario.papel !== 'ADMIN') return <Navigate to="/" replace />
  return children
}
