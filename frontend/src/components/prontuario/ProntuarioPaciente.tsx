import { useState } from 'react'
import { prontuarioApi } from '@/api/prontuarioApi'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import { AREA_FICHA_LABEL, TIPO_REGISTRO_LABEL } from '@/types'
import type { Atendimento, ConteudoFicha, ModeloFicha, RegistroClinico, TipoRegistro, VersaoRegistro } from '@/types'
import { formatDataHora } from '@/utils/format'
import { faltando } from '@/lib/ficha'
import { FormularioFicha, RespostasFicha } from './FormularioFicha'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'

interface Props {
  clienteId: string
  /** Só quem tem cadastro de profissional escreve no prontuário. */
  podeRegistrar: boolean
  atendimentos: Atendimento[]
}

type Edicao =
  | { modo: 'novo'; modelo: ModeloFicha | null }
  | { modo: 'corrigir'; registro: RegistroClinico }

/** Aba Prontuário: linha do tempo, novo registro, correção e histórico de versões. */
export function ProntuarioPaciente({ clienteId, podeRegistrar, atendimentos }: Props) {
  const { data: registros, loading, error, refetch } = useApi(() => prontuarioApi.listar(clienteId), [clienteId])
  const { data: modelos } = useApi(() => (podeRegistrar ? prontuarioApi.modelos() : Promise.resolve([])), [podeRegistrar])

  const [filtro, setFiltro] = useState<TipoRegistro | ''>('')
  const [edicao, setEdicao] = useState<Edicao | null>(null)
  const [valores, setValores] = useState<ConteudoFicha>({})
  const [motivo, setMotivo] = useState('')
  const [atendimentoId, setAtendimentoId] = useState('')
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [historico, setHistorico] = useState<{ registro: RegistroClinico; versoes: VersaoRegistro[] | null } | null>(
    null,
  )
  const [historicoErro, setHistoricoErro] = useState('')

  const abrirNovo = () => {
    setEdicao({ modo: 'novo', modelo: null })
    setValores({})
    setAtendimentoId('')
    setErro('')
  }

  const escolherModelo = (id: string) => {
    setEdicao({ modo: 'novo', modelo: (modelos ?? []).find((m) => m.id === id) ?? null })
    setValores({})
  }

  const abrirCorrecao = (registro: RegistroClinico) => {
    setEdicao({ modo: 'corrigir', registro })
    setValores({ ...registro.atual.conteudo })
    setMotivo('')
    setErro('')
  }

  const abrirHistorico = async (registro: RegistroClinico) => {
    setHistorico({ registro, versoes: null })
    setHistoricoErro('')
    try {
      setHistorico({ registro, versoes: await prontuarioApi.historico(registro.id) })
    } catch (err) {
      setHistoricoErro(err instanceof Error ? err.message : 'Falha ao carregar o histórico.')
    }
  }

  const campos =
    edicao?.modo === 'corrigir' ? edicao.registro.atual.modelo.campos : edicao?.modelo ? edicao.modelo.campos : []

  const salvar = async () => {
    if (!edicao) return
    if (edicao.modo === 'novo' && !edicao.modelo) return setErro('Escolha o formulário.')
    const vazios = faltando(campos, valores)
    if (vazios.length > 0) return setErro(`Preencha: ${vazios.join(', ')}.`)
    if (edicao.modo === 'corrigir' && !motivo.trim()) return setErro('Diga o motivo da correção.')
    setSalvando(true)
    setErro('')
    try {
      if (edicao.modo === 'novo' && edicao.modelo) {
        await prontuarioApi.registrar(clienteId, {
          modeloId: edicao.modelo.id,
          atendimentoId: atendimentoId || null,
          conteudo: valores,
        })
      } else if (edicao.modo === 'corrigir') {
        await prontuarioApi.corrigir(edicao.registro.id, valores, motivo.trim())
      }
      setEdicao(null)
      refetch()
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao salvar no prontuário.')
    } finally {
      setSalvando(false)
    }
  }

  const visiveis = (registros ?? []).filter((r) => !filtro || r.tipo === filtro)
  const atendimentosLigaveis = atendimentos.filter((a) => a.status !== 'CANCELADO')

  return (
    <section>
      <div className="flex flex-col gap-3 border-b border-hairline pb-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="font-display text-2xl font-medium text-ink">Prontuário</h2>
          <p className="mt-1 text-sm text-ink-soft">
            Nada é apagado: uma correção vira uma versão nova, com o motivo, e as anteriores ficam no histórico.
          </p>
        </div>
        {podeRegistrar && (
          <Button onClick={abrirNovo} className="w-full sm:w-auto">
            Novo registro
          </Button>
        )}
      </div>

      <div className="pt-6">
        {error && <ErrorBanner message={error} />}
        {(registros?.length ?? 0) > 0 && (
          <div className="mb-4 max-w-xs">
            <Select label="Mostrar" value={filtro} onChange={(e) => setFiltro(e.target.value as TipoRegistro | '')}>
              <option value="">Todos os registros</option>
              {(Object.keys(TIPO_REGISTRO_LABEL) as TipoRegistro[]).map((t) => (
                <option key={t} value={t}>
                  {TIPO_REGISTRO_LABEL[t]}
                </option>
              ))}
            </Select>
          </div>
        )}
        {loading ? (
          <Spinner />
        ) : visiveis.length === 0 ? (
          <EmptyState message={filtro ? 'Nenhum registro deste tipo.' : 'Nenhum registro no prontuário ainda.'} />
        ) : (
          <ol className="relative border-l border-hairline pl-5">
            {visiveis.map((r) => (
              <li key={r.id} className="relative pb-8 last:pb-0">
                <span aria-hidden className="absolute -left-[25px] top-1.5 h-2.5 w-2.5 rounded-full bg-accent" />
                <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
                  <p className="text-sm font-medium text-ink">
                    {TIPO_REGISTRO_LABEL[r.tipo]}
                    <span className="font-normal text-ink-soft"> · {r.atual.modelo.nome}</span>
                  </p>
                  <span className="font-mono text-[11px] uppercase tracking-[0.14em] text-ink-soft">
                    {formatDataHora(r.criadoEm)}
                  </span>
                </div>
                <p className="mt-0.5 text-sm text-ink-soft">
                  {r.profissionalNome}
                  {r.dataAtendimento ? ` · sessão de ${formatDataHora(r.dataAtendimento)}` : ''}
                  {r.versoes > 1 && (
                    <span className="ml-2 border border-hairline px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-[0.14em]">
                      Corrigido · versão {r.atual.numero}
                    </span>
                  )}
                </p>
                <div className="mt-3">
                  <RespostasFicha campos={r.atual.modelo.campos} conteudo={r.atual.conteudo} />
                </div>
                <div className="mt-2 flex flex-wrap gap-x-4">
                  {r.versoes > 1 && (
                    <Button variant="ghost" size="sm" onClick={() => abrirHistorico(r)}>
                      Ver as {r.versoes} versões
                    </Button>
                  )}
                  {r.podeCorrigir && (
                    <Button variant="ghost" size="sm" onClick={() => abrirCorrecao(r)}>
                      Corrigir
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>

      <Modal
        open={edicao !== null}
        title={edicao?.modo === 'corrigir' ? 'Corrigir registro' : 'Novo registro'}
        onClose={() => setEdicao(null)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex max-h-[70vh] flex-col gap-5 overflow-y-auto pr-1">
          {edicao?.modo === 'novo' && (
            <>
              <Select
                label="Formulário *"
                value={edicao.modelo?.id ?? ''}
                onChange={(e) => escolherModelo(e.target.value)}
              >
                <option value="">Escolha...</option>
                {(Object.keys(TIPO_REGISTRO_LABEL) as TipoRegistro[]).map((tipo) => {
                  const doTipo = (modelos ?? []).filter((m) => m.tipo === tipo)
                  if (doTipo.length === 0) return null
                  return (
                    <optgroup key={tipo} label={TIPO_REGISTRO_LABEL[tipo]}>
                      {doTipo.map((m) => (
                        <option key={m.id} value={m.id}>
                          {m.nome} ({AREA_FICHA_LABEL[m.area]})
                        </option>
                      ))}
                    </optgroup>
                  )
                })}
              </Select>
              {atendimentosLigaveis.length > 0 && (
                <Select label="Sessão (opcional)" value={atendimentoId} onChange={(e) => setAtendimentoId(e.target.value)}>
                  <option value="">Sem vínculo com sessão</option>
                  {atendimentosLigaveis.map((a) => (
                    <option key={a.id} value={a.id}>
                      {formatDataHora(a.dataAtendimento)}
                    </option>
                  ))}
                </Select>
              )}
            </>
          )}
          {edicao?.modo === 'corrigir' && (
            <p className="text-sm text-ink-soft">
              A versão {edicao.registro.atual.numero} continua guardada no histórico. Esta será a versão{' '}
              {edicao.registro.atual.numero + 1}.
            </p>
          )}
          {campos.length > 0 && (
            <FormularioFicha
              campos={campos}
              valores={valores}
              onChange={(v) => {
                setValores(v)
                setErro('')
              }}
            />
          )}
          {edicao?.modo === 'corrigir' && (
            <TextField
              label="Motivo da correção *"
              value={motivo}
              maxLength={500}
              onChange={(e) => setMotivo(e.target.value)}
              placeholder="Ex.: nota de dor digitada errado"
            />
          )}
          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="mt-2 flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setEdicao(null)} disabled={salvando}>
              Cancelar
            </Button>
            <Button onClick={salvar} disabled={salvando}>
              {salvando ? 'Salvando...' : edicao?.modo === 'corrigir' ? 'Salvar correção' : 'Registrar'}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal open={historico !== null} title="Histórico de versões" onClose={() => setHistorico(null)}>
        <div className="flex max-h-[70vh] flex-col gap-6 overflow-y-auto pr-1">
          {historicoErro && <p className="text-sm text-red-600">{historicoErro}</p>}
          {!historico?.versoes && !historicoErro && <Spinner />}
          {historico?.versoes?.map((v) => (
            <div key={v.numero} className="border-b border-hairline pb-5 last:border-b-0">
              <p className={microLabel}>
                Versão {v.numero} · {formatDataHora(v.criadoEm)} · {v.autorNome}
              </p>
              {v.motivo && <p className="mt-1 text-sm text-ink">Motivo: {v.motivo}</p>}
              <div className="mt-3">
                <RespostasFicha campos={v.modelo.campos} conteudo={v.conteudo} />
              </div>
            </div>
          ))}
        </div>
      </Modal>
    </section>
  )
}
