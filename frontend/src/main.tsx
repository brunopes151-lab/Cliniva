import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import { MarcaProvider } from '@/components/marca/MarcaProvider'
import { AuthProvider } from '@/hooks/useAuth'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <MarcaProvider>
          <App />
        </MarcaProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)