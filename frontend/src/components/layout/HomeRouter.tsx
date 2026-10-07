import { Navigate } from 'react-router-dom'
import { Spinner } from '@/components/ui/Spinner'
import { useAuth } from '@/hooks/useAuth'
import { cadastroPublicoAtivo } from '@/lib/config'
import { telaInicial } from '@/lib/perfis'
import { LandingPage } from '@/pages/LandingPage'

export function HomeRouter() {
  const { usuario, carregando } = useAuth()

  if (carregando) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-ivory">
        <Spinner />
      </div>
    )
  }

  if (usuario) {
    return <Navigate to={telaInicial(usuario)} replace />
  }

  // A landing é a página de venda da plataforma; sem cadastro público, a
  // entrada é o login.
  return cadastroPublicoAtivo ? <LandingPage /> : <Navigate to="/login" replace />
}