import { http } from './http'
import type { ConteudoFicha, ModeloFicha, ModeloFichaInput, RegistroClinico, VersaoRegistro } from '@/types'

export const prontuarioApi = {
  modelos: (todos = false) => http.get<ModeloFicha[]>(`/fichas/modelos${todos ? '?todos=true' : ''}`),
  criarModelo: (input: ModeloFichaInput) => http.post<ModeloFicha>('/fichas/modelos', input),
  novaVersaoModelo: (familiaId: string, input: ModeloFichaInput) =>
    http.post<ModeloFicha>(`/fichas/modelos/${familiaId}/versoes`, input),
  ativarModelo: (familiaId: string, ativo: boolean) =>
    http.patch<ModeloFicha>(`/fichas/modelos/${familiaId}/ativo`, { ativo }),
  listar: (clienteId: string) => http.get<RegistroClinico[]>(`/clientes/${clienteId}/prontuario`),
  registrar: (clienteId: string, input: { modeloId: string; atendimentoId?: string | null; conteudo: ConteudoFicha }) =>
    http.post<RegistroClinico>(`/clientes/${clienteId}/prontuario`, input),
  historico: (registroId: string) => http.get<VersaoRegistro[]>(`/prontuario/${registroId}/versoes`),
  corrigir: (registroId: string, conteudo: ConteudoFicha, motivo: string) =>
    http.post<RegistroClinico>(`/prontuario/${registroId}/versoes`, { conteudo, motivo }),
}
