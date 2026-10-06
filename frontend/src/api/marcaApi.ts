import { http } from './http'

export interface Marca {
  nome: string
  logoDataUrl: string | null
}

export const marcaApi = {
  atual: () => http.get<Marca>('/clinica/marca'),
  atualizar: (marca: Marca) => http.put<Marca>('/clinica/marca', marca),
  publica: (slug?: string) =>
    http.get<Marca>(slug ? `/public/marca?slug=${encodeURIComponent(slug)}` : '/public/marca'),
}
