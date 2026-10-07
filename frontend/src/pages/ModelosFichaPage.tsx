import { useState } from 'react'
import { prontuarioApi } from '@/api/prontuarioApi'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { PageHeader } from '@/components/ui/PageHeader'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import { AREA_FICHA_LABEL, TIPO_CAMPO_LABEL, TIPO_REGISTRO_LABEL } from '@/types'
import type { AreaFicha, CampoFicha, ModeloFicha, ModeloFichaInput, TipoCampo, TipoRegistro } from '@/types'

/** Pergunta em edição: as opções ficam como texto, uma por linha. */
interface CampoEditavel extends CampoFicha {
  opcoesTexto: string
}

interface Formulario {
  nome: string
  area: AreaFicha
  tipo: TipoRegistro
  campos: CampoEditavel[]
}

const vazio: Formulario = { nome: '', area: 'GERAL', tipo: 'EVOLUCAO', campos: [] }

// Pergunta nova: o identificador "novo-..." só existe na tela e é trocado ao salvar.
function novoCampo(atuais: CampoEditavel[]): CampoEditavel {
  return {
    id: `novo-${atuais.length}-${Date.now()}`,
    rotulo: '',
    tipo: 'TEXTO_LONGO',
    obrigatorio: false,
    opcoes: null,
    opcoesTexto: '',
  }
}

/** Identificador da resposta a partir do texto da pergunta (só para perguntas novas). */
function gerarId(rotulo: string, usados: Set<string>): string {
  const base =
    rotulo
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .replace(/^(\d)/, 'c_$1')
      .slice(0, 34) || 'pergunta'
  let id = base
  let n = 2
  while (usados.has(id)) id = `${base}_${n++}`
  return id
}

export function ModelosFichaPage() {
  const { data: modelos, loading, error, refetch } = useApi(() => prontuarioApi.modelos(true))

  const [editando, setEditando] = useState<ModeloFicha | null>(null)
  const [aberto, setAberto] = useState(false)
  const [form, setForm] = useState<Formulario>(vazio)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [acaoErro, setAcaoErro] = useState('')

  const abrir = (modelo: ModeloFicha | null) => {
    setEditando(modelo)
    setForm(
      modelo
        ? {
            nome: modelo.nome,
            area: modelo.area,
            tipo: modelo.tipo,
            campos: modelo.campos.map((c) => ({ ...c, opcoesTexto: (c.opcoes ?? []).join('\n') })),
          }
        : { ...vazio, campos: [novoCampo([])] },
    )
    setErro('')
    setAberto(true)
  }

  const mudarCampo = (i: number, patch: Partial<CampoEditavel>) =>
    setForm({ ...form, campos: form.campos.map((c, j) => (j === i ? { ...c, ...patch } : c)) })

  const mover = (i: number, delta: number) => {
    const campos = [...form.campos]
    const [c] = campos.splice(i, 1)
    campos.splice(i + delta, 0, c)
    setForm({ ...form, campos })
  }

  const salvar = async () => {
    if (!form.nome.trim()) return setErro('Dê um nome ao modelo.')
    if (form.campos.length === 0) return setErro('Inclua pelo menos uma pergunta.')
    if (form.campos.some((c) => !c.rotulo.trim())) return setErro('Toda pergunta precisa de um texto.')
    // Perguntas existentes mantêm o identificador; as novas ganham um a partir do texto.
    const usados = new Set(form.campos.filter((c) => !c.id.startsWith('novo-')).map((c) => c.id))
    const campos: CampoFicha[] = form.campos.map((c) => {
      const id = c.id.startsWith('novo-') ? gerarId(c.rotulo, usados) : c.id
      usados.add(id)
      return {
        id,
        rotulo: c.rotulo.trim(),
        tipo: c.tipo,
        obrigatorio: c.obrigatorio,
        opcoes:
          c.tipo === 'OPCOES'
            ? c.opcoesTexto
                .split('\n')
                .map((o) => o.trim())
                .filter(Boolean)
            : null,
      }
    })
    if (campos.some((c) => c.tipo === 'OPCOES' && (c.opcoes?.length ?? 0) < 2))
      return setErro('Perguntas de escolha precisam de pelo menos duas opções.')
    const input: ModeloFichaInput = { nome: form.nome.trim(), area: form.area, tipo: form.tipo, campos }
    setSalvando(true)
    setErro('')
    try {
      if (editando) await prontuarioApi.novaVersaoModelo(editando.familiaId, input)
      else await prontuarioApi.criarModelo(input)
      setAberto(false)
      refetch()
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao salvar o modelo.')
    } finally {
      setSalvando(false)
    }
  }

  const alternarAtivo = async (m: ModeloFicha) => {
    setAcaoErro('')
    try {
      await prontuarioApi.ativarModelo(m.familiaId, !m.ativo)
      refetch()
    } catch (err) {
      setAcaoErro(err instanceof Error ? err.message : 'Falha ao alterar o modelo.')
    }
  }

  return (
    <>
      <PageHeader
        kicker="Prontuário"
        title="Modelos de ficha"
        subtitle="Formulários de anamnese, avaliação, plano, evolução e reavaliação"
        action={
          <Button onClick={() => abrir(null)} className="w-full lg:w-auto">
            Novo modelo
          </Button>
        }
      />
      <p className="-mt-4 mb-8 max-w-2xl text-sm text-ink-soft">
        Editar um modelo cria uma versão nova. Os registros já feitos continuam mostrando as perguntas da versão que
        usaram.
      </p>

      {error && <ErrorBanner message={error} />}
      {acaoErro && <ErrorBanner message={acaoErro} />}

      {loading ? (
        <Spinner />
      ) : !modelos || modelos.length === 0 ? (
        <EmptyState message="Nenhum modelo cadastrado." />
      ) : (
        <ul className="divide-y divide-hairline border-y border-hairline">
          {modelos.map((m) => (
            <li key={m.familiaId} className="flex flex-col gap-2 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p className="text-sm font-medium text-ink">
                  {m.nome}
                  {!m.ativo && (
                    <span className="ml-2 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">
                      Desativado
                    </span>
                  )}
                </p>
                <p className="mt-0.5 text-sm text-ink-soft">
                  {TIPO_REGISTRO_LABEL[m.tipo]} · {AREA_FICHA_LABEL[m.area]} · {m.campos.length} perguntas · versão{' '}
                  {m.versao}
                </p>
              </div>
              <div className="flex gap-4">
                <Button variant="ghost" size="sm" onClick={() => abrir(m)}>
                  Editar
                </Button>
                <Button variant={m.ativo ? 'dangerText' : 'ghost'} size="sm" onClick={() => alternarAtivo(m)}>
                  {m.ativo ? 'Desativar' : 'Reativar'}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal
        open={aberto}
        title={editando ? `Editar modelo (versão ${editando.versao + 1})` : 'Novo modelo'}
        onClose={() => setAberto(false)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex max-h-[70vh] flex-col gap-5 overflow-y-auto pr-1">
          <TextField label="Nome *" value={form.nome} onChange={(e) => setForm({ ...form, nome: e.target.value })} />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Select label="Área" value={form.area} onChange={(e) => setForm({ ...form, area: e.target.value as AreaFicha })}>
              {(Object.keys(AREA_FICHA_LABEL) as AreaFicha[]).map((a) => (
                <option key={a} value={a}>
                  {AREA_FICHA_LABEL[a]}
                </option>
              ))}
            </Select>
            <Select
              label="Tipo de registro"
              value={form.tipo}
              disabled={!!editando}
              onChange={(e) => setForm({ ...form, tipo: e.target.value as TipoRegistro })}
            >
              {(Object.keys(TIPO_REGISTRO_LABEL) as TipoRegistro[]).map((t) => (
                <option key={t} value={t}>
                  {TIPO_REGISTRO_LABEL[t]}
                </option>
              ))}
            </Select>
          </div>

          <div>
            <p className="mb-2 text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft">Perguntas</p>
            <ol className="flex flex-col gap-3">
              {form.campos.map((c, i) => (
                <li key={c.id} className="border border-hairline bg-ivory p-4">
                  <div className="flex flex-col gap-3">
                    <TextField
                      label={`Pergunta ${i + 1}`}
                      value={c.rotulo}
                      onChange={(e) => mudarCampo(i, { rotulo: e.target.value })}
                    />
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                      <Select
                        label="Resposta"
                        value={c.tipo}
                        className="flex-1"
                        onChange={(e) => mudarCampo(i, { tipo: e.target.value as TipoCampo })}
                      >
                        {(Object.keys(TIPO_CAMPO_LABEL) as TipoCampo[]).map((t) => (
                          <option key={t} value={t}>
                            {TIPO_CAMPO_LABEL[t]}
                          </option>
                        ))}
                      </Select>
                      <label className="flex items-center gap-2 pb-2 text-sm text-ink">
                        <input
                          type="checkbox"
                          checked={c.obrigatorio}
                          onChange={(e) => mudarCampo(i, { obrigatorio: e.target.checked })}
                        />
                        Obrigatória
                      </label>
                    </div>
                    {c.tipo === 'OPCOES' && (
                      <label className="flex flex-col">
                        <span className="mb-1 text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft">
                          Opções (uma por linha)
                        </span>
                        <textarea
                          rows={3}
                          className="w-full resize-y border-0 border-b border-hairline bg-transparent px-0 py-2 text-sm text-ink outline-none focus:border-accent"
                          value={c.opcoesTexto}
                          onChange={(e) => mudarCampo(i, { opcoesTexto: e.target.value })}
                        />
                      </label>
                    )}
                    <div className="flex gap-4">
                      <Button variant="ghost" size="sm" disabled={i === 0} onClick={() => mover(i, -1)}>
                        Subir
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={i === form.campos.length - 1}
                        onClick={() => mover(i, 1)}
                      >
                        Descer
                      </Button>
                      <Button
                        variant="dangerText"
                        size="sm"
                        onClick={() => setForm({ ...form, campos: form.campos.filter((_, j) => j !== i) })}
                      >
                        Remover
                      </Button>
                    </div>
                  </div>
                </li>
              ))}
            </ol>
            <Button
              variant="secondary"
              size="sm"
              className="mt-3"
              onClick={() => setForm({ ...form, campos: [...form.campos, novoCampo(form.campos)] })}
            >
              + Pergunta
            </Button>
          </div>

          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setAberto(false)} disabled={salvando}>
              Cancelar
            </Button>
            <Button onClick={salvar} disabled={salvando}>
              {salvando ? 'Salvando...' : editando ? 'Salvar nova versão' : 'Criar modelo'}
            </Button>
          </div>
        </div>
      </Modal>
    </>
  )
}
