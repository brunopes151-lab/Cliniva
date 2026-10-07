import { useState } from 'react'
import { pacotesApi } from '@/api/pacotesApi'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import { SITUACAO_PACOTE_LABEL } from '@/types'
import type { PacoteCliente, SituacaoPacote } from '@/types'
import { formatData, formatDataHora, formatMoeda } from '@/utils/format'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'

const SITUACAO_COR: Record<SituacaoPacote, string> = {
  ATIVO: 'text-sage-dark',
  ESGOTADO: 'text-ink-soft',
  VENCIDO: 'text-red-600',
  CANCELADO: 'text-ink-soft',
}

function hojeIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

interface Props {
  clienteId: string
  podeVender: boolean
}

/** Pacotes do paciente na ficha: saldo, validade, baixas e estornos. */
export function PacotesDoCliente({ clienteId, podeVender }: Props) {
  const { data: pacotes, error, refetch } = useApi(() => pacotesApi.doCliente(clienteId), [clienteId])
  const { data: modelos } = useApi(() => (podeVender ? pacotesApi.listar() : Promise.resolve([])), [podeVender])
  const aVenda = (modelos ?? []).filter((m) => m.ativo)

  const [vendendo, setVendendo] = useState(false)
  const [pacoteId, setPacoteId] = useState('')
  const [dataCompra, setDataCompra] = useState(hojeIso())
  const [valorPago, setValorPago] = useState('')
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [cancelando, setCancelando] = useState<PacoteCliente | null>(null)
  const [cancelErro, setCancelErro] = useState('')
  const [abertos, setAbertos] = useState<Record<string, boolean>>({})

  const abrirVenda = () => {
    const primeiro = aVenda[0]
    setPacoteId(primeiro?.id ?? '')
    setValorPago(primeiro ? String(primeiro.preco) : '')
    setDataCompra(hojeIso())
    setErro('')
    setVendendo(true)
  }

  const escolher = (id: string) => {
    setPacoteId(id)
    const m = aVenda.find((x) => x.id === id)
    if (m) setValorPago(String(m.preco))
  }

  const vender = async () => {
    if (!pacoteId) return setErro('Escolha o pacote.')
    setSalvando(true)
    setErro('')
    try {
      await pacotesApi.vender(clienteId, {
        pacoteId,
        dataCompra,
        valorPago: valorPago === '' ? undefined : Number(valorPago),
      })
      setVendendo(false)
      refetch()
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao registrar a venda.')
    } finally {
      setSalvando(false)
    }
  }

  const confirmarCancelamento = async () => {
    if (!cancelando) return
    setSalvando(true)
    setCancelErro('')
    try {
      await pacotesApi.cancelar(clienteId, cancelando.id)
      setCancelando(null)
      refetch()
    } catch (err) {
      setCancelErro(err instanceof Error ? err.message : 'Falha ao cancelar o pacote.')
    } finally {
      setSalvando(false)
    }
  }

  const modeloEscolhido = aVenda.find((m) => m.id === pacoteId)

  return (
    <section>
      <div className="flex items-baseline justify-between gap-4 border-b border-hairline pb-3">
        <h2 className="font-display text-2xl font-medium text-ink">Pacotes</h2>
        {podeVender && (
          <Button size="sm" variant="ghost" onClick={abrirVenda} disabled={aVenda.length === 0}>
            Vender pacote
          </Button>
        )}
      </div>
      <div className="pt-6">
        {error && <ErrorBanner message={error} />}
        {!pacotes || pacotes.length === 0 ? (
          <EmptyState message="Nenhum pacote comprado." />
        ) : (
          <ul className="divide-y divide-hairline border-y border-hairline">
            {pacotes.map((p) => {
              const usadas = p.sessoesTotal - p.saldo
              return (
                <li key={p.id} className="py-4">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-ink">{p.nome}</p>
                      <p className="mt-0.5 text-sm text-ink-soft">
                        {p.servicoNome} · comprado em {formatData(p.dataCompra)} por {formatMoeda(p.valorPago)}
                      </p>
                      <p className="mt-0.5 text-sm text-ink-soft">Válido até {formatData(p.dataValidade)}</p>
                    </div>
                    <div className="flex items-center gap-4 sm:flex-col sm:items-end sm:gap-1">
                      <span className="font-display text-2xl font-medium text-ink">
                        {p.saldo}
                        <span className="text-sm text-ink-soft">/{p.sessoesTotal}</span>
                      </span>
                      <span className={`text-[11px] font-medium uppercase tracking-[0.14em] ${SITUACAO_COR[p.situacao]}`}>
                        {SITUACAO_PACOTE_LABEL[p.situacao]}
                      </span>
                    </div>
                  </div>
                  <div className="mt-2 h-1.5 w-full bg-hairline" aria-hidden>
                    <div
                      className="h-full bg-accent"
                      style={{ width: `${p.sessoesTotal ? (p.saldo / p.sessoesTotal) * 100 : 0}%` }}
                    />
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
                    <span className={microLabel}>
                      {usadas} {usadas === 1 ? 'sessão usada' : 'sessões usadas'}
                    </span>
                    {p.movimentos.length > 0 && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setAbertos({ ...abertos, [p.id]: !abertos[p.id] })}
                      >
                        {abertos[p.id] ? 'Ocultar histórico' : 'Ver histórico'}
                      </Button>
                    )}
                    {podeVender && p.situacao !== 'CANCELADO' && (
                      <Button variant="dangerText" size="sm" onClick={() => setCancelando(p)}>
                        Cancelar pacote
                      </Button>
                    )}
                  </div>
                  {abertos[p.id] && (
                    <ul className="mt-2 border-l border-hairline pl-3">
                      {p.movimentos.map((m, i) => (
                        <li key={i} className="py-1 text-sm text-ink-soft">
                          <span className={m.tipo === 'BAIXA' ? 'text-ink' : 'text-sage-dark'}>
                            {m.tipo === 'BAIXA' ? 'Baixa' : 'Estorno'}
                          </span>{' '}
                          {m.dataAtendimento
                            ? `· sessão de ${formatDataHora(m.dataAtendimento)}`
                            : `· ${formatDataHora(m.criadoEm)}`}
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <Modal open={vendendo} title="Vender pacote" onClose={() => setVendendo(false)}>
        <div className="flex flex-col gap-5">
          <Select label="Pacote *" value={pacoteId} onChange={(e) => escolher(e.target.value)}>
            {aVenda.map((m) => (
              <option key={m.id} value={m.id}>
                {m.nome} ({m.sessoes} sessões)
              </option>
            ))}
          </Select>
          {modeloEscolhido && (
            <p className="text-sm text-ink-soft">
              {modeloEscolhido.servicoNome} · validade de {modeloEscolhido.validadeDias} dias a partir da compra
            </p>
          )}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <TextField
              label="Data da compra"
              type="date"
              value={dataCompra}
              onChange={(e) => setDataCompra(e.target.value)}
            />
            <TextField
              label="Valor pago (R$)"
              type="number"
              min={0}
              step="0.01"
              value={valorPago}
              onChange={(e) => setValorPago(e.target.value)}
            />
          </div>
          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setVendendo(false)}>
              Cancelar
            </Button>
            <Button onClick={vender} disabled={salvando}>
              {salvando ? 'Salvando...' : 'Registrar venda'}
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={cancelando !== null}
        title="Cancelar pacote"
        message={`Cancelar "${cancelando?.nome}"? O saldo restante (${cancelando?.saldo ?? 0} sessões) deixa de ser usado. O histórico continua na ficha.`}
        confirmLabel="Cancelar pacote"
        error={cancelErro}
        pending={salvando}
        pendingLabel="Cancelando..."
        onConfirm={confirmarCancelamento}
        onCancel={() => {
          if (salvando) return
          setCancelando(null)
          setCancelErro('')
        }}
      />
    </section>
  )
}
