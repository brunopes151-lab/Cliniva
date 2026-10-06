import { useContext } from 'react'
import { MarcaContext } from '@/lib/marca'

/** Nome e logo da clínica, configurados em /configuracoes. */
export function useMarca() {
  return useContext(MarcaContext)
}
