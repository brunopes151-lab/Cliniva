import { lgpdApi } from '@/api/lgpdApi'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Spinner } from '@/components/ui/Spinner'
import { useApi } from '@/hooks/useApi'
import { ACAO_AUDITORIA_LABEL, PAPEL_LABEL } from '@/types'
import { formatDataHora } from '@/utils/format'

/** Aba Acessos: quem abriu, escreveu ou exportou dados de saúde deste paciente. */
export function AcessosPaciente({ clienteId }: { clienteId: string }) {
  const { data: acessos, loading, error } = useApi(() => lgpdApi.auditoria(clienteId), [clienteId])

  return (
    <section>
      <div className="border-b border-hairline pb-3">
        <h2 className="font-display text-2xl font-medium text-ink">Acessos</h2>
        <p className="mt-1 text-sm text-ink-soft">
          Cada abertura do prontuário, registro, correção, exportação e mudança no termo fica aqui. Esta lista não se
          altera nem se apaga.
        </p>
      </div>
      <div className="pt-6">
        {error && <ErrorBanner message={error} />}
        {loading ? (
          <Spinner />
        ) : !acessos || acessos.length === 0 ? (
          <EmptyState message="Nenhum acesso registrado ainda." />
        ) : (
          <ul className="border-t border-hairline">
            {acessos.map((a, i) => (
              <li
                key={`${a.em}-${i}`}
                className="flex flex-col gap-1 border-b border-hairline py-3 sm:flex-row sm:items-baseline sm:justify-between sm:gap-6"
              >
                <div className="min-w-0">
                  <p className="text-sm text-ink">
                    <span className="font-medium">{a.usuario}</span>
                    <span className="text-ink-soft"> · {PAPEL_LABEL[a.papel]}</span>
                    {a.modoSuporte && (
                      <span className="ml-2 border border-hairline px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-[0.14em]">
                        Suporte
                      </span>
                    )}
                  </p>
                  <p className="mt-0.5 text-sm text-ink-soft">{ACAO_AUDITORIA_LABEL[a.acao]}</p>
                </div>
                <p className="shrink-0 font-mono text-[11px] uppercase tracking-[0.14em] text-ink-soft">
                  {formatDataHora(a.em)}
                  {a.ip ? ` · ${a.ip}` : ''}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
