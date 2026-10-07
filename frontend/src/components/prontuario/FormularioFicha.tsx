import type { CampoFicha, ConteudoFicha, RespostaFicha } from '@/types'
import { formatarResposta } from '@/lib/ficha'

const microLabel = 'mb-1 block text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'
const campoBase =
  'w-full border-0 border-b border-hairline bg-transparent px-0 py-2.5 text-sm text-ink outline-none transition-colors duration-150 ease-in-out hover:border-ink/50 focus:border-accent'

interface Props {
  campos: CampoFicha[]
  valores: ConteudoFicha
  onChange: (valores: ConteudoFicha) => void
}

/** Formulário montado a partir dos campos do modelo de ficha. */
export function FormularioFicha({ campos, valores, onChange }: Props) {
  const definir = (id: string, valor: RespostaFicha | undefined) => {
    const novo = { ...valores }
    if (valor === undefined || valor === '') delete novo[id]
    else novo[id] = valor
    onChange(novo)
  }

  return (
    <div className="flex flex-col gap-5">
      {campos.map((campo) => {
        const rotulo = `${campo.rotulo}${campo.obrigatorio ? ' *' : ''}`
        const valor = valores[campo.id]
        const htmlId = `campo-${campo.id}`
        switch (campo.tipo) {
          case 'TEXTO_LONGO':
            return (
              <label key={campo.id} className="flex flex-col" htmlFor={htmlId}>
                <span className={microLabel}>{rotulo}</span>
                <textarea
                  id={htmlId}
                  rows={3}
                  className={`${campoBase} resize-y`}
                  value={typeof valor === 'string' ? valor : ''}
                  onChange={(e) => definir(campo.id, e.target.value)}
                />
              </label>
            )
          case 'SIM_NAO':
            return (
              <fieldset key={campo.id}>
                <legend className={microLabel}>{rotulo}</legend>
                <div className="flex gap-2">
                  {[
                    { label: 'Sim', v: true },
                    { label: 'Não', v: false },
                  ].map((opcao) => (
                    <button
                      key={opcao.label}
                      type="button"
                      aria-pressed={valor === opcao.v}
                      onClick={() => definir(campo.id, valor === opcao.v ? undefined : opcao.v)}
                      className={`cursor-pointer border px-4 py-1.5 text-sm transition-colors ${
                        valor === opcao.v ? 'border-accent bg-accent/15 text-ink' : 'border-hairline text-ink-soft'
                      }`}
                    >
                      {opcao.label}
                    </button>
                  ))}
                </div>
              </fieldset>
            )
          case 'ESCALA':
            return (
              <fieldset key={campo.id}>
                <legend className={microLabel}>{rotulo}</legend>
                <div className="grid grid-cols-6 gap-1.5 sm:grid-cols-11">
                  {Array.from({ length: 11 }, (_, n) => (
                    <button
                      key={n}
                      type="button"
                      aria-pressed={valor === n}
                      onClick={() => definir(campo.id, valor === n ? undefined : n)}
                      className={`cursor-pointer border py-1.5 font-mono text-[13px] transition-colors ${
                        valor === n ? 'border-accent bg-accent/15 text-ink' : 'border-hairline text-ink-soft'
                      }`}
                    >
                      {n}
                    </button>
                  ))}
                </div>
              </fieldset>
            )
          case 'OPCOES':
            return (
              <label key={campo.id} className="flex flex-col" htmlFor={htmlId}>
                <span className={microLabel}>{rotulo}</span>
                <select
                  id={htmlId}
                  className={`${campoBase} cursor-pointer`}
                  value={typeof valor === 'string' ? valor : ''}
                  onChange={(e) => definir(campo.id, e.target.value)}
                >
                  <option value="">—</option>
                  {(campo.opcoes ?? []).map((o) => (
                    <option key={o} value={o}>
                      {o}
                    </option>
                  ))}
                </select>
              </label>
            )
          default:
            return (
              <label key={campo.id} className="flex flex-col" htmlFor={htmlId}>
                <span className={microLabel}>{rotulo}</span>
                <input
                  id={htmlId}
                  type={campo.tipo === 'NUMERO' ? 'number' : campo.tipo === 'DATA' ? 'date' : 'text'}
                  step="any"
                  className={campoBase}
                  value={valor === undefined ? '' : String(valor)}
                  onChange={(e) =>
                    definir(
                      campo.id,
                      campo.tipo === 'NUMERO'
                        ? e.target.value === ''
                          ? undefined
                          : Number(e.target.value)
                        : e.target.value,
                    )
                  }
                />
              </label>
            )
        }
      })}
    </div>
  )
}

/** Respostas de uma versão, na ordem do formulário que ela usou. */
export function RespostasFicha({ campos, conteudo }: { campos: CampoFicha[]; conteudo: ConteudoFicha }) {
  const respondidos = campos.filter((c) => conteudo[c.id] !== undefined)
  if (respondidos.length === 0) return <p className="text-sm text-ink-soft">Sem respostas.</p>
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
      {respondidos.map((c) => (
        <div key={c.id} className={c.tipo === 'TEXTO_LONGO' ? 'sm:col-span-2' : ''}>
          <dt className="text-[11px] font-medium uppercase tracking-[0.14em] text-ink-soft">{c.rotulo}</dt>
          <dd className="mt-0.5 whitespace-pre-line text-sm text-ink">{formatarResposta(c, conteudo[c.id])}</dd>
        </div>
      ))}
    </dl>
  )
}
