import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { lgpdApi } from '@/api/lgpdApi'
import { RespostasFicha } from '@/components/prontuario/FormularioFicha'
import { Button } from '@/components/ui/Button'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Spinner } from '@/components/ui/Spinner'
import { useApi } from '@/hooks/useApi'
import { baixarJson } from '@/lib/exportacao'
import { SITUACAO_PACOTE_LABEL, TIPO_REGISTRO_LABEL } from '@/types'
import {
  canalLabel,
  clienteStatusLabel,
  formatData,
  formatDataHora,
  formatMoeda,
  origemLabel,
  statusLabel,
} from '@/utils/format'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'

function Bloco({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="mt-10 break-inside-avoid-page">
      <h2 className="border-b border-hairline pb-2 font-display text-xl font-medium text-ink">{titulo}</h2>
      <div className="pt-4 text-sm text-ink">{children}</div>
    </section>
  )
}

function Linha({ rotulo, valor }: { rotulo: string; valor: ReactNode }) {
  return (
    <div className="grid grid-cols-[9rem_1fr] gap-3 py-1">
      <dt className="text-ink-soft">{rotulo}</dt>
      <dd>{valor || '—'}</dd>
    </div>
  )
}

function Vazio() {
  return <p className="text-ink-soft">Nada registrado.</p>
}

/**
 * Exportação legível: o navegador imprime ou salva em PDF. Abrir esta tela
 * já gera a exportação e fica registrado nos acessos do paciente.
 */
export function ExportacaoPacientePage() {
  const { id } = useParams<{ id: string }>()
  const { data, loading, error } = useApi(() => lgpdApi.exportar(id ?? ''), [id])

  if (loading) return <Spinner />

  return (
    <div className="mx-auto max-w-3xl bg-bone px-4 py-8 sm:px-8 print:max-w-none print:bg-white print:px-0 print:py-0">
      <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between print:hidden">
        <Link className={`${microLabel} hover:text-ink`} to={`/clientes/${id}`}>
          ← Voltar para a ficha
        </Link>
        {data && (
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button size="sm" onClick={() => window.print()}>
              Imprimir ou salvar em PDF
            </Button>
            <Button variant="secondary" size="sm" onClick={() => baixarJson(data)}>
              Baixar arquivo (JSON)
            </Button>
          </div>
        )}
      </div>

      {error && <ErrorBanner message={error} />}
      {data && (
        <article>
          <p className={microLabel}>{data.clinica}</p>
          <h1 className="mt-2 font-display text-3xl font-medium text-ink">Dados do paciente</h1>
          <p className="mt-2 text-sm text-ink-soft">
            {data.cadastro.nome} · gerado em {formatDataHora(data.geradoEm)} por {data.geradoPor}, a pedido do
            titular (Lei 13.709/2018, art. 18).
          </p>

          <Bloco titulo="Cadastro">
            <dl>
              <Linha rotulo="Nome" valor={data.cadastro.nome} />
              <Linha rotulo="Telefone" valor={data.cadastro.telefone} />
              <Linha rotulo="E-mail" valor={data.cadastro.email} />
              <Linha
                rotulo="Nascimento"
                valor={data.cadastro.dataNascimento ? formatData(data.cadastro.dataNascimento) : null}
              />
              <Linha rotulo="Situação" valor={clienteStatusLabel(data.cadastro.status)} />
              <Linha rotulo="Origem" valor={data.cadastro.origem ? origemLabel(data.cadastro.origem) : null} />
              <Linha
                rotulo="Canal preferido"
                valor={data.cadastro.canalPreferido ? canalLabel(data.cadastro.canalPreferido) : null}
              />
              <Linha rotulo="Preferências" valor={data.cadastro.preferencias} />
              <Linha rotulo="Observações" valor={data.cadastro.observacoes} />
            </dl>
          </Bloco>

          <Bloco titulo="Termo de consentimento">
            {data.consentimentos.length === 0 ? (
              <Vazio />
            ) : (
              <ul className="flex flex-col gap-2">
                {data.consentimentos.map((c) => (
                  <li key={c.id}>
                    Versão {c.versaoTermo}: aceito em {formatDataHora(c.aceitoEm)} (registrado por {c.registradoPor})
                    {c.revogadoEm
                      ? `; revogado em ${formatDataHora(c.revogadoEm)}${c.motivoRevogacao ? ` (${c.motivoRevogacao})` : ''}`
                      : '; vigente'}
                    .
                  </li>
                ))}
              </ul>
            )}
            {data.termosAceitos.map((t) => (
              <div key={t.id} className="mt-4 break-inside-avoid-page">
                <p className={microLabel}>Texto da versão {t.versao}</p>
                <p className="mt-2 whitespace-pre-line border border-hairline p-3 leading-relaxed">{t.texto}</p>
              </div>
            ))}
          </Bloco>

          <Bloco titulo="Sessões">
            {data.atendimentos.length === 0 ? (
              <Vazio />
            ) : (
              <ul className="divide-y divide-hairline">
                {data.atendimentos.map((a) => (
                  <li key={a.id} className="py-2">
                    {formatDataHora(a.dataAtendimento)} · {statusLabel(a.status)} · {a.profissionalNome}
                    <span className="text-ink-soft">
                      {' '}
                      · {a.servicos.map((s) => s.nomeServico).join(', ')} · {formatMoeda(a.valorTotal)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Bloco>

          <Bloco titulo="Pacotes">
            {data.pacotes.length === 0 ? (
              <Vazio />
            ) : (
              <ul className="divide-y divide-hairline">
                {data.pacotes.map((p) => (
                  <li key={p.id} className="py-2">
                    {p.nome} · comprado em {formatData(p.dataCompra)} por {formatMoeda(p.valorPago)} ·{' '}
                    {p.sessoesTotal - p.saldo} de {p.sessoesTotal} sessões usadas · {SITUACAO_PACOTE_LABEL[p.situacao]}
                  </li>
                ))}
              </ul>
            )}
          </Bloco>

          <Bloco titulo="Anotações">
            {data.anotacoes.length === 0 ? (
              <Vazio />
            ) : (
              <ul className="divide-y divide-hairline">
                {data.anotacoes.map((n) => (
                  <li key={n.id} className="py-2">
                    <span className="text-ink-soft">{formatDataHora(n.criadaEm)} · </span>
                    {n.texto}
                  </li>
                ))}
              </ul>
            )}
          </Bloco>

          <Bloco titulo="Prontuário">
            {data.prontuario.length === 0 ? (
              <Vazio />
            ) : (
              <ol className="flex flex-col gap-8">
                {data.prontuario.map(({ registro, versoes }) => (
                  <li key={registro.id} className="break-inside-avoid-page">
                    <p className="font-medium">
                      {TIPO_REGISTRO_LABEL[registro.tipo]} · {formatDataHora(registro.criadoEm)} ·{' '}
                      {registro.profissionalNome}
                    </p>
                    {versoes.map((v) => (
                      <div key={v.numero} className="mt-3 border-l border-hairline pl-4">
                        <p className={microLabel}>
                          Versão {v.numero} · {formatDataHora(v.criadoEm)} · {v.autorNome}
                          {v.numero === versoes[0].numero ? ' · atual' : ''}
                        </p>
                        {v.motivo && <p className="mt-1">Motivo da correção: {v.motivo}</p>}
                        <div className="mt-2">
                          <RespostasFicha campos={v.modelo.campos} conteudo={v.conteudo} />
                        </div>
                      </div>
                    ))}
                  </li>
                ))}
              </ol>
            )}
          </Bloco>
        </article>
      )}
    </div>
  )
}
