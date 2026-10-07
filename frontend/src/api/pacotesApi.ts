import { http } from './http'
import type { Pacote, PacoteCliente, PacoteInput, VenderPacoteInput } from '@/types'

export const pacotesApi = {
  listar: () => http.get<Pacote[]>('/pacotes'),
  criar: (input: PacoteInput) => http.post<Pacote>('/pacotes', input),
  atualizar: (id: string, input: PacoteInput) => http.put<Pacote>(`/pacotes/${id}`, input),
  doCliente: (clienteId: string) => http.get<PacoteCliente[]>(`/clientes/${clienteId}/pacotes`),
  vender: (clienteId: string, input: VenderPacoteInput) =>
    http.post<PacoteCliente>(`/clientes/${clienteId}/pacotes`, input),
  cancelar: (clienteId: string, id: string) =>
    http.post<PacoteCliente>(`/clientes/${clienteId}/pacotes/${id}/cancelar`, {}),
}
