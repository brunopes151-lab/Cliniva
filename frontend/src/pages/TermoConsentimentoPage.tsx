import { useState } from 'react'
import { lgpdApi } from '@/api/lgpdApi'
import { Button } from '@/components/ui/Button'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { PageHeader } from '@/components/ui/PageHeader'
import { Spinner } from '@/components/ui/Spinner'
import { useApi } from '@/hooks/useApi'
import { formatDataHora } from '@/utils/format'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'

/** Texto do termo de consentimento. Publicar cria a versão seguinte; nenhuma versão se edita. */
export function TermoConsentimentoPage() {
  const { data: versoes, loading, error, refetch } = useApi(async () => {
    await lgpdApi.termo() // garante a versão 1 na primeira visita
    return lgpdApi.versoesDoTermo()
  })
  const [texto, setTexto] = useState<string | null>(null)
  const [erro, setErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [aberta, setAberta] = useState<number | null>(null)

  const atual = versoes?.[0]
  const rascunho = texto ?? atual?.texto ?? ''
  const mudou = !!atual && rascunho.trim() !== atual.texto

  const publicar = async () => {
    setSalvando(true)
    setErro('')
    try {
      await lgpdApi.publicarTermo(rascunho)
      setTexto(null)
      await refetch()
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Falha ao publicar o termo.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <>
      <PageHeader
        kicker="LGPD"
        title="Termo de consentimento"
        subtitle="O texto que o paciente aceita antes de qualquer registro no prontuário"
      />
      <p className="-mt-4 mb-8 max-w-2xl text-sm text-ink-soft">
        O texto inicial é um ponto de partida: revise com quem cuida da parte jurídica da clínica. Publicar cria uma
        versão nova; quem aceitou a anterior continua com o aceite valendo, e a ficha avisa para colher o da versão
        nova.
      </p>

      {error && <ErrorBanner message={error} />}
      {loading ? (
        <Spinner />
      ) : (
        atual && (
          <div className="grid grid-cols-1 gap-12 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <p className={microLabel}>
                Versão {atual.versao} · em vigor desde {formatDataHora(atual.vigenteDesde)}
              </p>
              <textarea
                aria-label="Texto do termo"
                rows={18}
                value={rascunho}
                onChange={(e) => setTexto(e.target.value)}
                className="mt-3 w-full resize-y border border-hairline bg-ivory p-4 text-sm leading-relaxed text-ink outline-none focus:border-accent"
              />
              {erro && <p className="mt-2 text-sm text-red-600">{erro}</p>}
              <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:justify-end">
                {mudou && (
                  <Button variant="ghost" onClick={() => setTexto(null)} disabled={salvando}>
                    Descartar mudanças
                  </Button>
                )}
                <Button onClick={publicar} disabled={!mudou || salvando || !rascunho.trim()}>
                  {salvando ? 'Publicando...' : `Publicar versão ${atual.versao + 1}`}
                </Button>
              </div>
            </div>
            <div>
              <p className={microLabel}>Versões publicadas</p>
              <ul className="mt-3 border-t border-hairline">
                {versoes?.map((v) => (
                  <li key={v.id} className="border-b border-hairline py-3">
                    <button
                      className="w-full cursor-pointer text-left text-sm text-ink"
                      onClick={() => setAberta(aberta === v.versao ? null : v.versao)}
                    >
                      Versão {v.versao}
                      <span className="text-ink-soft">
                        {' '}
                        · {formatDataHora(v.vigenteDesde)}
                        {v.publicadoPor ? ` · ${v.publicadoPor}` : ''}
                      </span>
                    </button>
                    {aberta === v.versao && (
                      <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink-soft">{v.texto}</p>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )
      )}
    </>
  )
}
