import { createContext } from 'react'
import type { Marca } from '@/api/marcaApi'

// Valor enquanto a marca real não chega (e quando a API não responde). É o
// único nome escrito no código, e é neutro de propósito.
export const MARCA_PADRAO: Marca = { nome: 'Minha Clínica', logoDataUrl: null }

export interface MarcaContextValue {
  marca: Marca
  definirMarca: (marca: Marca) => void
}

export const MarcaContext = createContext<MarcaContextValue>({
  marca: MARCA_PADRAO,
  definirMarca: () => {},
})
