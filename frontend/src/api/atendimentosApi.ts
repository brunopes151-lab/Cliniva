import { http } from './http'
import type {
  Atendimento,
  AtendimentoFiltros,
  AtendimentoInput,
  AtendimentoResumo,
  AtendimentoUpdate,
  PreviaSerie,
  SerieCriada,
  SerieInput,
  StatusAtendimento,
} from '@/types'

function buildQuery(filtros: AtendimentoFiltros): string {
  const params = new URLSearchParams()
  if (filtros.status) params.set('status', filtros.status)
  if (filtros.clienteId) params.set('clienteId', filtros.clienteId)
  if (filtros.profissionalId) params.set('profissionalId', filtros.profissionalId)
  if (filtros.dataInicio) params.set('dataInicio', filtros.dataInicio)
  if (filtros.dataFim) params.set('dataFim', filtros.dataFim)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export const atendimentosApi = {
  listar: (filtros: AtendimentoFiltros = {}) => http.get<AtendimentoResumo[]>(`/atendimentos${buildQuery(filtros)}`),
  buscar: (id: string) => http.get<Atendimento>(`/atendimentos/${id}`),
  criar: (input: AtendimentoInput) => http.post<Atendimento>('/atendimentos', input),
  atualizar: (id: string, input: AtendimentoUpdate) => http.put<Atendimento>(`/atendimentos/${id}`, input),
  alterarStatus: (id: string, novoStatus: StatusAtendimento) =>
    http.patch<Atendimento>(`/atendimentos/${id}/status`, { novoStatus }),
  previaSerie: (input: SerieInput) => http.post<PreviaSerie>('/atendimentos/series/previa', input),
  criarSerie: (input: SerieInput) => http.post<SerieCriada>('/atendimentos/series', input),
  cancelarSeguintes: (id: string) =>
    http.post<{ cancelados: number }>(`/atendimentos/${id}/cancelar-seguintes`, {}),
}