import type { ExportacaoPaciente } from '@/types'

/** Baixa a exportação como arquivo JSON (o paciente recebe esse arquivo). */
export function baixarJson(dados: ExportacaoPaciente) {
  const blob = new Blob([JSON.stringify(dados, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `dados-do-paciente-${dados.geradoEm.slice(0, 10)}.json`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
