import { http } from './http'
import type { BookingInput, BookingResult, DisponibilidadeDia, ProfissionalPublico, ServicoPublico } from '@/types'

export const bookingApi = {
  listarServicos: (slug: string) => http.get<ServicoPublico[]>(`/public/booking/${slug}/servicos`),
  listarProfissionais: (slug: string, servicoId: string) =>
    http.get<ProfissionalPublico[]>(`/public/booking/${slug}/profissionais?servicoId=${servicoId}`),
  disponibilidade: (slug: string, data: string, servicoId: string, profissionalId?: string | null) =>
    http.get<DisponibilidadeDia>(
      `/public/booking/${slug}/disponibilidade?data=${data}&servicoId=${servicoId}` +
        (profissionalId ? `&profissionalId=${profissionalId}` : ''),
    ),
  agendar: (slug: string, input: BookingInput) => http.post<BookingResult>(`/public/booking/${slug}`, input),
}
