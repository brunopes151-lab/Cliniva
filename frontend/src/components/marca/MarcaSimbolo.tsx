import type { Marca } from '@/api/marcaApi'

interface MarcaSimboloProps {
  marca: Marca
  className?: string
}

/** Logo da clínica; sem logo, um monograma neutro com a inicial do nome. */
export function MarcaSimbolo({ marca, className = 'h-8 w-8' }: MarcaSimboloProps) {
  if (marca.logoDataUrl) {
    return <img src={marca.logoDataUrl} alt={`Logo de ${marca.nome}`} className={`${className} object-contain`} />
  }
  const inicial = marca.nome.trim().charAt(0).toUpperCase() || 'C'
  return (
    <span
      aria-hidden="true"
      className={`${className} inline-flex shrink-0 items-center justify-center bg-accent font-display text-lg text-carbon`}
    >
      {inicial}
    </span>
  )
}
