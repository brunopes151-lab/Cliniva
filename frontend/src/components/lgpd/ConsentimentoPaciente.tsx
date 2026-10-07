import { useState } from 'react'
import { lgpdApi } from '@/api/lgpdApi'
import { Button } from '@/components/ui/Button'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/TextField'
import type { ConsentimentoPaciente as Consentimento, SituacaoConsentimento } from '@/types'
import { formatDataHora } from '@/utils/format'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'

interface Props {
  clienteId: string
  situacao: SituacaoConsentimento | null
  loading: boolean
  error: string | null
  /** A equipe da clínica registra; o suporte da plataforma só olha. */
  podeRegistrar: boolean
  onMudou: () => void
}

/** Termo de consentimento na ficha: situação, aceite e revogação. */
export function ConsentimentoPaciente({ clienteId, situacao, loading, error, podeRegistrar, onMudou }: Props) {
  const [lendo, setLendo] = useState(false)
  const [concordou, setConcordou] = useState(false)
  const [revogando, setRevogando] = useState<Consentimento | null>(null)
  const [motivo, setMotivo] = useState('')
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)

  const abrirTermo = () => {
    setConcordou(false)
    setErro('')
    setLendo(true)
  }

  const registrar = async () => {
    if (!situacao) return
    setSalvando(true)
    setErro('')
    try {
      await lgpdApi.registrarAceite(clienteId, situacao.termoAtual.id)
      onMudou()
      setLendo(false)
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao registrar o aceite.')
    } finally {
      setSalvando(false)
    }
  }

  const revogar = async () => {
    if (!revogando) return
    if (!motivo.trim()) return setErro('Diga o motivo da revogação.')
    setSalvando(true)
    setErro('')
    try {
      await lgpdApi.revogar(revogando.id, motivo.trim())
      onMudou()
      setRevogando(null)
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao registrar a revogação.')
    } finally {
      setSalvando(false)
    }
  }

  const atual = situacao?.consentimentos.find((c) => c.vigente) ?? null

  return (
    <section>
      <div className="flex items-baseline justify-between gap-4 border-b border-hairline pb-3">
        <h2 className="font-display text-2xl font-medium text-ink">Termo LGPD</h2>
      </div>
      <div className="pt-6">
        {error && <ErrorBanner message={error} />}
        {loading || !situacao ? (
          !error && <Spinner />
        ) : (
          <>
            {atual ? (
              <div>
                <p className="text-sm font-medium text-ink">Aceito</p>
                <p className="mt-1 text-sm text-ink-soft">
                  Versão {atual.versaoTermo} em {formatDataHora(atual.aceitoEm)}, registrado por {atual.registradoPor}.
                </p>
                {!situacao.aceitouVersaoAtual && (
                  <p className="mt-3 border-l-2 border-accent pl-3 text-sm text-ink">
                    O termo mudou para a versão {situacao.termoAtual.versao}. Colha o aceite da versão nova na
                    próxima visita.
                  </p>
                )}
              </div>
            ) : (
              <div className="border-l-2 border-red-600 pl-3">
                <p className="text-sm font-medium text-ink">
                  {situacao.consentimentos.length > 0 ? 'Revogado' : 'Pendente'}
                </p>
                <p className="mt-1 text-sm text-ink-soft">
                  Sem aceite vigente, ninguém faz registro novo no prontuário deste paciente.
                </p>
              </div>
            )}

            {podeRegistrar && (
              <div className="mt-4 flex flex-wrap gap-x-4 gap-y-2">
                {!situacao.aceitouVersaoAtual && (
                  <Button size="sm" onClick={abrirTermo}>
                    Registrar aceite
                  </Button>
                )}
                {atual && (
                  <Button
                    variant="dangerText"
                    size="sm"
                    onClick={() => {
                      setMotivo('')
                      setErro('')
                      setRevogando(atual)
                    }}
                  >
                    Revogar
                  </Button>
                )}
              </div>
            )}

            {situacao.consentimentos.length > 0 && (
              <ul className="mt-6 border-t border-hairline">
                {situacao.consentimentos.map((c) => (
                  <li key={c.id} className="border-b border-hairline py-3 text-sm">
                    <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-ink-soft">
                      Versão {c.versaoTermo} · {formatDataHora(c.aceitoEm)}
                    </p>
                    <p className="mt-1 text-ink">
                      {c.vigente ? 'Aceito' : 'Revogado'} · {c.registradoPor}
                    </p>
                    {c.revogadoEm && (
                      <p className="mt-1 text-ink-soft">
                        Revogado em {formatDataHora(c.revogadoEm)} por {c.revogadoPor}
                        {c.motivoRevogacao ? `: ${c.motivoRevogacao}` : ''}
                      </p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>

      <Modal
        open={lendo}
        title={`Termo de consentimento · versão ${situacao?.termoAtual.versao ?? ''}`}
        onClose={() => setLendo(false)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex max-h-[70vh] flex-col gap-5 overflow-y-auto pr-1">
          <p className={microLabel}>Leia com o paciente</p>
          <div className="whitespace-pre-line border border-hairline bg-ivory p-4 text-sm leading-relaxed text-ink">
            {situacao?.termoAtual.texto}
          </div>
          <label className="flex items-start gap-3 text-sm text-ink">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={concordou}
              onChange={(e) => setConcordou(e.target.checked)}
            />
            O paciente leu este texto e concordou com ele.
          </label>
          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setLendo(false)} disabled={salvando}>
              Cancelar
            </Button>
            <Button onClick={registrar} disabled={!concordou || salvando}>
              {salvando ? 'Salvando...' : 'Registrar aceite'}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        open={revogando !== null}
        title="Revogar consentimento"
        onClose={() => setRevogando(null)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex flex-col gap-5">
          <p className="text-sm text-ink-soft">
            Depois da revogação, ninguém faz registro novo no prontuário. O que já foi registrado continua guardado
            pelo prazo legal.
          </p>
          <TextField
            label="Motivo *"
            value={motivo}
            maxLength={500}
            onChange={(e) => setMotivo(e.target.value)}
            placeholder="Ex.: pedido do paciente por e-mail"
          />
          {erro && <p className="text-sm text-red-600">{erro}</p>}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setRevogando(null)} disabled={salvando}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={revogar} disabled={salvando}>
              {salvando ? 'Salvando...' : 'Revogar'}
            </Button>
          </div>
        </div>
      </Modal>
    </section>
  )
}
