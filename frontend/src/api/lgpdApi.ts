import { http } from './http'
import type { AcessoAuditoria, ExportacaoPaciente, SituacaoConsentimento, TermoConsentimento } from '@/types'

export const lgpdApi = {
  termo: () => http.get<TermoConsentimento>('/lgpd/termo'),
  versoesDoTermo: () => http.get<TermoConsentimento[]>('/lgpd/termo/versoes'),
  publicarTermo: (texto: string) => http.post<TermoConsentimento>('/lgpd/termo', { texto }),
  situacao: (clienteId: string) => http.get<SituacaoConsentimento>(`/clientes/${clienteId}/consentimentos`),
  registrarAceite: (clienteId: string, termoId: string) =>
    http.post<SituacaoConsentimento>(`/clientes/${clienteId}/consentimentos`, { termoId }),
  revogar: (consentimentoId: string, motivo: string) =>
    http.post<SituacaoConsentimento>(`/consentimentos/${consentimentoId}/revogacao`, { motivo }),
  auditoria: (clienteId: string) => http.get<AcessoAuditoria[]>(`/clientes/${clienteId}/auditoria`),
  exportar: (clienteId: string) => http.get<ExportacaoPaciente>(`/clientes/${clienteId}/exportacao`),
}
