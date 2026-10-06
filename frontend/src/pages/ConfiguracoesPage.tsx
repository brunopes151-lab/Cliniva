import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { ApiError } from '@/api/http'
import { marcaApi } from '@/api/marcaApi'
import { MarcaSimbolo } from '@/components/marca/MarcaSimbolo'
import { Button } from '@/components/ui/Button'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { PageHeader } from '@/components/ui/PageHeader'
import { TextField } from '@/components/ui/TextField'
import { useMarca } from '@/hooks/useMarca'
import { reduzirLogo } from '@/utils/imagem'

export function ConfiguracoesPage() {
  const { marca, definirMarca } = useMarca()
  const [nome, setNome] = useState(marca.nome)
  const [logo, setLogo] = useState<string | null>(marca.logoDataUrl)
  const [erro, setErro] = useState('')
  const [salvo, setSalvo] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const arquivoRef = useRef<HTMLInputElement>(null)

  // A marca chega depois do primeiro render; o formulário acompanha até a
  // pessoa começar a editar.
  const editadoRef = useRef(false)
  useEffect(() => {
    if (editadoRef.current) return
    setNome(marca.nome)
    setLogo(marca.logoDataUrl)
  }, [marca])

  const escolherLogo = async (evento: ChangeEvent<HTMLInputElement>) => {
    const arquivo = evento.target.files?.[0]
    evento.target.value = ''
    if (!arquivo) return
    setErro('')
    try {
      editadoRef.current = true
      setLogo(await reduzirLogo(arquivo))
      setSalvo(false)
    } catch (err) {
      setErro(err instanceof Error ? err.message : 'Não foi possível usar essa imagem.')
    }
  }

  const salvar = async (evento: FormEvent) => {
    evento.preventDefault()
    setErro('')
    setSalvo(false)
    if (!nome.trim()) {
      setErro('Informe o nome da clínica.')
      return
    }
    setSalvando(true)
    try {
      const atualizada = await marcaApi.atualizar({ nome: nome.trim(), logoDataUrl: logo })
      definirMarca(atualizada)
      editadoRef.current = false
      setSalvo(true)
    } catch (err) {
      setErro(err instanceof ApiError || err instanceof Error ? err.message : 'Não foi possível salvar.')
    } finally {
      setSalvando(false)
    }
  }

  const previa = { nome: nome.trim() || marca.nome, logoDataUrl: logo }

  return (
    <div>
      <PageHeader
        kicker="Clínica"
        title="Configurações"
        subtitle="Nome e logo aparecem no menu, na tela de login, no agendamento online e nas mensagens de WhatsApp."
      />
      {erro && <ErrorBanner message={erro} />}
      <form onSubmit={salvar} className="flex max-w-xl flex-col gap-8">
        <TextField
          label="Nome da clínica *"
          value={nome}
          maxLength={120}
          onChange={(e) => {
            editadoRef.current = true
            setNome(e.target.value)
            setSalvo(false)
          }}
        />

        <div className="flex flex-col gap-3">
          <span className="text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft">Logo</span>
          <div className="flex flex-wrap items-center gap-4">
            <MarcaSimbolo marca={previa} className="h-16 w-16" />
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" onClick={() => arquivoRef.current?.click()}>
                {logo ? 'Trocar logo' : 'Enviar logo'}
              </Button>
              {logo && (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    editadoRef.current = true
                    setLogo(null)
                    setSalvo(false)
                  }}
                >
                  Remover
                </Button>
              )}
            </div>
          </div>
          <p className="text-xs text-ink-soft">PNG, JPEG ou WebP. A imagem é reduzida para até 256 px.</p>
          <input
            ref={arquivoRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="hidden"
            onChange={escolherLogo}
          />
        </div>

        <div className="flex flex-wrap items-center gap-4">
          <Button type="submit" disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </Button>
          {salvo && <span className="text-sm text-ink-soft">Salvo.</span>}
        </div>
      </form>
    </div>
  )
}
