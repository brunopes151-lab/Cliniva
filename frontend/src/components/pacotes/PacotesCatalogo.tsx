import { useState } from 'react'
import { pacotesApi } from '@/api/pacotesApi'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import type { Pacote, PacoteInput, Servico } from '@/types'
import { formatMoeda } from '@/utils/format'

interface Props {
  servicos: Servico[]
  podeEditar: boolean
}

const formVazio: PacoteInput = { nome: '', servicoId: '', sessoes: 10, validadeDias: 180, preco: 0, ativo: true }

/** Modelos de pacote (ex.: 10 sessões de drenagem em 6 meses) no catálogo de serviços. */
export function PacotesCatalogo({ servicos, podeEditar }: Props) {
  const { data: pacotes, error, refetch } = useApi(() => pacotesApi.listar())

  const [aberto, setAberto] = useState(false)
  const [editando, setEditando] = useState<Pacote | null>(null)
  const [form, setForm] = useState<PacoteInput>(formVazio)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)

  const abrirNovo = () => {
    setEditando(null)
    setForm({ ...formVazio, servicoId: servicos[0]?.id ?? '' })
    setErro('')
    setAberto(true)
  }

  const abrirEditar = (p: Pacote) => {
    setEditando(p)
    setForm({
      nome: p.nome,
      servicoId: p.servicoId,
      sessoes: p.sessoes,
      validadeDias: p.validadeDias,
      preco: p.preco,
      ativo: p.ativo,
    })
    setErro('')
    setAberto(true)
  }

  const salvar = async () => {
    if (!form.nome.trim()) return setErro('Nome é obrigatório.')
    if (!form.servicoId) return setErro('Escolha o serviço do pacote.')
    if (!form.sessoes || form.sessoes < 1 || form.sessoes > 100) return setErro('O pacote tem de 1 a 100 sessões.')
    if (!form.validadeDias || form.validadeDias < 1 || form.validadeDias > 1095)
      return setErro('A validade vai de 1 a 1095 dias.')
    if (form.preco < 0) return setErro('Preço não pode ser negativo.')
    setSalvando(true)
    setErro('')
    try {
      const input = { ...form, nome: form.nome.trim() }
      if (editando) await pacotesApi.atualizar(editando.id, input)
      else await pacotesApi.criar(input)
      setAberto(false)
      refetch()
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao salvar o pacote.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <section className="mt-14">
      <div className="mb-4 flex flex-col gap-3 border-b border-hairline pb-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="font-display text-2xl font-medium text-ink">Pacotes</h2>
          <p className="mt-1 text-sm text-ink-soft">
            Sessões vendidas antecipadamente. Cada sessão concluída dá baixa no saldo do paciente.
          </p>
        </div>
        {podeEditar && (
          <Button onClick={abrirNovo} disabled={servicos.length === 0} className="w-full sm:w-auto">
            Novo pacote
          </Button>
        )}
      </div>

      {error && <ErrorBanner message={error} />}

      {!pacotes || pacotes.length === 0 ? (
        <EmptyState message="Nenhum pacote cadastrado." />
      ) : (
        <ul className="divide-y divide-hairline border-y border-hairline">
          {pacotes.map((p) => (
            <li key={p.id} className="flex flex-col gap-2 py-3.5 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink">
                  {p.nome}
                  {!p.ativo && (
                    <span className="ml-2 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">
                      Inativo
                    </span>
                  )}
                </p>
                <p className="mt-0.5 text-sm text-ink-soft">
                  {p.servicoNome} · {p.sessoes} sessões · validade de {p.validadeDias} dias
                </p>
              </div>
              <div className="flex items-center justify-between gap-4 sm:justify-end">
                <span className="font-mono text-[13px] text-accent-strong">{formatMoeda(p.preco)}</span>
                {podeEditar && (
                  <Button variant="ghost" size="sm" onClick={() => abrirEditar(p)}>
                    Editar
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={aberto} title={editando ? 'Editar pacote' : 'Novo pacote'} onClose={() => setAberto(false)}>
        <div className="flex flex-col gap-5">
          <TextField
            label="Nome *"
            value={form.nome}
            onChange={(e) => setForm({ ...form, nome: e.target.value })}
            placeholder="Ex.: Drenagem 10 sessões"
          />
          <Select
            label="Serviço *"
            value={form.servicoId}
            onChange={(e) => setForm({ ...form, servicoId: e.target.value })}
          >
            {servicos.map((s) => (
              <option key={s.id} value={s.id}>
                {s.nome}
              </option>
            ))}
          </Select>
          <div className="grid grid-cols-2 gap-4">
            <TextField
              label="Sessões *"
              type="number"
              min={1}
              max={100}
              value={form.sessoes}
              onChange={(e) => setForm({ ...form, sessoes: Number(e.target.value) })}
            />
            <TextField
              label="Validade (dias) *"
              type="number"
              min={1}
              max={1095}
              value={form.validadeDias}
              onChange={(e) => setForm({ ...form, validadeDias: Number(e.target.value) })}
            />
          </div>
          <TextField
            label="Preço do pacote (R$) *"
            type="number"
            min={0}
            step="0.01"
            value={form.preco === 0 ? '' : form.preco}
            onChange={(e) => setForm({ ...form, preco: Number(e.target.value) })}
            placeholder="0,00"
          />
          {editando && (
            <label className="flex items-center gap-2 text-sm text-ink">
              <input
                type="checkbox"
                checked={form.ativo ?? true}
                onChange={(e) => setForm({ ...form, ativo: e.target.checked })}
              />
              Disponível para venda
            </label>
          )}
          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setAberto(false)}>
              Cancelar
            </Button>
            <Button onClick={salvar} disabled={salvando}>
              {salvando ? 'Salvando...' : editando ? 'Salvar alterações' : 'Cadastrar'}
            </Button>
          </div>
        </div>
      </Modal>
    </section>
  )
}
