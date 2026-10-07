import { useState } from 'react'
import { Link } from 'react-router-dom'
import { lgpdApi } from '@/api/lgpdApi'
import { Button } from '@/components/ui/Button'
import { baixarJson } from '@/lib/exportacao'

/** Exportação a pedido do paciente (art. 18 da LGPD). Só o administrador. */
export function ExportarDados({ clienteId }: { clienteId: string }) {
  const [baixando, setBaixando] = useState(false)
  const [erro, setErro] = useState('')

  const baixar = async () => {
    setBaixando(true)
    setErro('')
    try {
      baixarJson(await lgpdApi.exportar(clienteId))
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao exportar.')
    } finally {
      setBaixando(false)
    }
  }

  return (
    <section>
      <div className="border-b border-hairline pb-3">
        <h2 className="font-display text-2xl font-medium text-ink">Dados do paciente</h2>
      </div>
      <div className="pt-6">
        <p className="text-sm text-ink-soft">
          Quando o paciente pedir uma cópia de tudo o que a clínica guarda sobre ele: cadastro, sessões, pacotes,
          anotações, prontuário com todas as versões e o termo aceito. Cada exportação fica registrada.
        </p>
        <div className="mt-4 flex flex-col gap-3 sm:flex-row">
          <Link
            to={`/clientes/${clienteId}/exportacao`}
            className="inline-flex items-center justify-center bg-accent px-4 py-2.5 text-[11px] font-medium uppercase tracking-[0.18em] text-carbon hover:bg-accent-strong"
          >
            Versão para imprimir ou PDF
          </Link>
          <Button variant="secondary" size="sm" onClick={baixar} disabled={baixando}>
            {baixando ? 'Gerando...' : 'Baixar arquivo (JSON)'}
          </Button>
        </div>
        {erro && <p className="mt-3 text-sm text-red-600">{erro}</p>}
      </div>
    </section>
  )
}
