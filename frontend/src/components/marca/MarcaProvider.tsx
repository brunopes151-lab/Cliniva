import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { marcaApi } from '@/api/marcaApi'
import type { Marca } from '@/api/marcaApi'
import { useAuth } from '@/hooks/useAuth'
import { MARCA_PADRAO, MarcaContext } from '@/lib/marca'

/**
 * Carrega a marca uma vez por sessão: a da clínica do usuário logado, ou a
 * pública (tela de login). O ADMIN sem clínica selecionada cai na pública.
 */
export function MarcaProvider({ children }: { children: ReactNode }) {
  const { usuario, carregando } = useAuth()
  const [marca, setMarca] = useState<Marca>(MARCA_PADRAO)
  const chave = usuario ? `${usuario.id}:${usuario.clinicaId ?? ''}` : 'publica'

  useEffect(() => {
    if (carregando) return
    let ativo = true
    const buscar = usuario ? marcaApi.atual().catch(() => marcaApi.publica()) : marcaApi.publica()
    buscar
      .then((m) => {
        if (ativo) setMarca(m)
      })
      .catch(() => {
        if (ativo) setMarca(MARCA_PADRAO)
      })
    return () => {
      ativo = false
    }
    // a chave resume o usuário: recarrega só quando quem está logado muda
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chave, carregando])

  useEffect(() => {
    document.title = marca.nome
  }, [marca.nome])

  const valor = useMemo(() => ({ marca, definirMarca: setMarca }), [marca])
  return <MarcaContext.Provider value={valor}>{children}</MarcaContext.Provider>
}
