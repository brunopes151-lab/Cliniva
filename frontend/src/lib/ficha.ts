import type { CampoFicha, ConteudoFicha, RespostaFicha } from '@/types'
import { formatData } from '@/utils/format'

/** Campos obrigatórios ainda vazios, para avisar antes de enviar. */
export function faltando(campos: CampoFicha[], valores: ConteudoFicha): string[] {
  return campos
    .filter((c) => c.obrigatorio)
    .filter((c) => {
      const v = valores[c.id]
      return v === undefined || (typeof v === 'string' && v.trim() === '')
    })
    .map((c) => c.rotulo)
}

export function formatarResposta(campo: CampoFicha, valor: RespostaFicha): string {
  if (campo.tipo === 'SIM_NAO') return valor ? 'Sim' : 'Não'
  if (campo.tipo === 'ESCALA') return `${valor} de 10`
  if (campo.tipo === 'DATA' && typeof valor === 'string') return formatData(valor)
  return String(valor)
}
