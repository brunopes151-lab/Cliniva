import { Navigate, Route, Routes } from 'react-router-dom'
import { RequerAdmin } from '@/components/auth/RequerAdmin'
import { RequerAuth } from '@/components/auth/RequerAuth'
import { RequerEquipeAdministrativa, RequerResponsavel } from '@/components/auth/RequerResponsavel'
import { AppLayout } from '@/components/layout/AppLayout'
import { HomeRouter } from '@/components/layout/HomeRouter'
import { AdminPage } from '@/pages/AdminPage'
import { AdminLoginPage } from '@/pages/AdminLoginPage'
import { AgendaPage } from '@/pages/AgendaPage'
import { AtendimentosPage } from '@/pages/AtendimentosPage'
import { BookingPage } from '@/pages/BookingPage'
import { CadastroPage } from '@/pages/CadastroPage'
import { ClienteDetalhePage } from '@/pages/ClienteDetalhePage'
import { ClientesPage } from '@/pages/ClientesPage'
import { ConfiguracoesPage } from '@/pages/ConfiguracoesPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { EstoquePage } from '@/pages/EstoquePage'
import { LoginPage } from '@/pages/LoginPage'
import { ProfissionaisPage } from '@/pages/ProfissionaisPage'
import { ServicosPage } from '@/pages/ServicosPage'
import { TrocarSenhaPage } from '@/pages/TrocarSenhaPage'
import { UsuariosPage } from '@/pages/UsuariosPage'
import { ModelosFichaPage } from '@/pages/ModelosFichaPage'
import { TermoConsentimentoPage } from '@/pages/TermoConsentimentoPage'
import { ExportacaoPacientePage } from '@/pages/ExportacaoPacientePage'
import { cadastroPublicoAtivo } from '@/lib/config'

function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRouter />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/login-admin" element={<AdminLoginPage />} />
      <Route
        path="/cadastro"
        element={cadastroPublicoAtivo ? <CadastroPage /> : <Navigate to="/login" replace />}
      />
      <Route path="/agendar/:slug" element={<BookingPage />} />
      {/* Fora do layout para imprimir só o documento. */}
      <Route
        path="/clientes/:id/exportacao"
        element={
          <RequerAuth>
            <RequerResponsavel>
              <ExportacaoPacientePage />
            </RequerResponsavel>
          </RequerAuth>
        }
      />
      <Route
        element={
          <RequerAuth>
            <AppLayout />
          </RequerAuth>
        }
      >
        <Route path="/dashboard" element={<RequerEquipeAdministrativa><DashboardPage /></RequerEquipeAdministrativa>} />
        <Route path="/clientes" element={<ClientesPage />} />
        <Route path="/clientes/:id" element={<ClienteDetalhePage />} />
        <Route path="/servicos" element={<ServicosPage />} />
        <Route path="/agenda" element={<AgendaPage />} />
        <Route path="/estoque" element={<RequerEquipeAdministrativa><EstoquePage /></RequerEquipeAdministrativa>} />
        <Route path="/atendimentos" element={<AtendimentosPage />} />
        <Route path="/profissionais" element={<RequerResponsavel><ProfissionaisPage /></RequerResponsavel>} />
        <Route path="/usuarios" element={<RequerResponsavel><UsuariosPage /></RequerResponsavel>} />
        <Route path="/fichas" element={<RequerResponsavel><ModelosFichaPage /></RequerResponsavel>} />
        <Route path="/termo" element={<RequerResponsavel><TermoConsentimentoPage /></RequerResponsavel>} />
        <Route path="/configuracoes" element={<RequerResponsavel><ConfiguracoesPage /></RequerResponsavel>} />
        <Route path="/admin" element={<RequerAdmin><AdminPage /></RequerAdmin>} />
        <Route path="/trocar-senha" element={<TrocarSenhaPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default App