import { http } from './http'
import type { AgendaItem, AgendaLink, DisponibilidadeDia, HorarioAtendimento } from '@/types'

function doProfissional(profissionalId?: string | null, primeiro = false): string {
  if (!profissionalId) return ''
  return `${primeiro ? '?' : '&'}profissionalId=${profissionalId}`
}

export const agendaApi = {
  listarDia: (data: string, profissionalId?: string | null) =>
    http.get<AgendaItem[]>(`/agenda?data=${data}${doProfissional(profissionalId)}`),
  linkPublico: () => http.get<AgendaLink>('/agenda/link'),
  disponibilidade: (data: string, servicoId: string, profissionalId?: string | null) =>
    http.get<DisponibilidadeDia>(
      `/agenda/disponibilidade?data=${data}&servicoId=${servicoId}${doProfissional(profissionalId)}`,
    ),
  /** Sem profissional = expediente padrão da clínica (o do "Geral"). */
  listarHorarios: (profissionalId?: string | null) =>
    http.get<HorarioAtendimento[]>(`/agenda/horarios${doProfissional(profissionalId, true)}`),
  atualizarHorarios: (horarios: HorarioAtendimento[], profissionalId?: string | null) =>
    http.put<HorarioAtendimento[]>(`/agenda/horarios${doProfissional(profissionalId, true)}`, horarios),
}
