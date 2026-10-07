import { useState } from 'react'
import { especialidadesApi, profissionaisApi } from '@/api/equipeApi'
import { servicosApi } from '@/api/servicosApi'
import { Button } from '@/components/ui/Button'
import { CardActions, CardDetail, CardItem, CardLabel, CardList } from '@/components/ui/CardList'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { PageHeader } from '@/components/ui/PageHeader'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import type { Especialidade, Profissional, ProfissionalInput, Servico } from '@/types'

const microLabel = 'text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft'
const COR_PADRAO = '#8A9A8E'

const formVazio: ProfissionalInput = { nome: '', cor: null, ativo: true, especialidadeIds: [], servicoIds: [] }

function alternar(lista: string[], id: string): string[] {
  return lista.includes(id) ? lista.filter((x) => x !== id) : [...lista, id]
}

function CorPonto({ cor }: { cor: string | null }) {
  return (
    <span
      aria-hidden
      className="inline-block h-3 w-3 shrink-0 rounded-full border border-hairline"
      style={{ backgroundColor: cor ?? 'transparent' }}
    />
  )
}

function Marcavel({ marcado, rotulo, onClick }: { marcado: boolean; rotulo: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={marcado}
      className={`cursor-pointer border px-3 py-2 text-left text-sm transition-colors duration-150 ease-in-out ${
        marcado ? 'border-accent bg-accent/10 text-ink' : 'border-hairline text-ink-soft hover:border-ink/40'
      }`}
    >
      {marcado ? '✓ ' : ''}
      {rotulo}
    </button>
  )
}

function resumoServicos(profissional: Profissional, servicos: Servico[] | null): string {
  if (profissional.servicoIds.length === 0) return 'Serviços sem vínculo'
  const nomes = profissional.servicoIds
    .map((id) => servicos?.find((s) => s.id === id)?.nome)
    .filter(Boolean)
  return nomes.join(', ') || `${profissional.servicoIds.length} serviço(s)`
}

export function ProfissionaisPage() {
  const { data: profissionais, loading, error, refetch } = useApi<Profissional[]>(() => profissionaisApi.listar())
  const {
    data: especialidades,
    error: erroEspecialidades,
    refetch: recarregarEspecialidades,
  } = useApi<Especialidade[]>(() => especialidadesApi.listar())
  const { data: servicos } = useApi<Servico[]>(() => servicosApi.listar())

  const [modalAberto, setModalAberto] = useState(false)
  const [editando, setEditando] = useState<Profissional | null>(null)
  const [form, setForm] = useState<ProfissionalInput>(formVazio)
  const [formErro, setFormErro] = useState('')
  const [salvando, setSalvando] = useState(false)

  const [novaEspecialidade, setNovaEspecialidade] = useState('')
  const [renomeando, setRenomeando] = useState<Especialidade | null>(null)
  const [nomeEspecialidade, setNomeEspecialidade] = useState('')
  const [excluindo, setExcluindo] = useState<Especialidade | null>(null)
  const [especialidadeErro, setEspecialidadeErro] = useState('')

  const abrirCriar = () => {
    setEditando(null)
    setForm({ ...formVazio, cor: COR_PADRAO })
    setFormErro('')
    setModalAberto(true)
  }

  const abrirEditar = (profissional: Profissional) => {
    setEditando(profissional)
    setForm({
      nome: profissional.nome,
      cor: profissional.cor,
      ativo: profissional.ativo,
      especialidadeIds: profissional.especialidades.map((e) => e.id),
      servicoIds: profissional.servicoIds,
    })
    setFormErro('')
    setModalAberto(true)
  }

  const salvar = async () => {
    if (!form.nome.trim()) {
      setFormErro('Informe o nome do profissional.')
      return
    }
    setSalvando(true)
    setFormErro('')
    try {
      const dados = { ...form, nome: form.nome.trim() }
      if (editando) {
        await profissionaisApi.atualizar(editando.id, dados)
      } else {
        await profissionaisApi.criar(dados)
      }
      setModalAberto(false)
      refetch()
    } catch (err) {
      setFormErro(err instanceof Error ? err.message : 'Falha ao salvar o profissional.')
    } finally {
      setSalvando(false)
    }
  }

  const criarEspecialidade = async () => {
    if (!novaEspecialidade.trim()) return
    setEspecialidadeErro('')
    try {
      await especialidadesApi.criar(novaEspecialidade.trim())
      setNovaEspecialidade('')
      recarregarEspecialidades()
    } catch (err) {
      setEspecialidadeErro(err instanceof Error ? err.message : 'Falha ao criar a especialidade.')
    }
  }

  const salvarRenomear = async () => {
    if (!renomeando || !nomeEspecialidade.trim()) return
    setEspecialidadeErro('')
    try {
      await especialidadesApi.renomear(renomeando.id, nomeEspecialidade.trim())
      setRenomeando(null)
      recarregarEspecialidades()
      refetch()
    } catch (err) {
      setEspecialidadeErro(err instanceof Error ? err.message : 'Falha ao renomear a especialidade.')
    }
  }

  const confirmarExcluir = async () => {
    if (!excluindo) return
    setSalvando(true)
    setEspecialidadeErro('')
    try {
      await especialidadesApi.excluir(excluindo.id)
      setExcluindo(null)
      recarregarEspecialidades()
      refetch()
    } catch (err) {
      setEspecialidadeErro(err instanceof Error ? err.message : 'Falha ao excluir a especialidade.')
    } finally {
      setSalvando(false)
    }
  }

  const especialidadesTexto = (p: Profissional) => p.especialidades.map((e) => e.nome).join(', ') || '—'

  return (
    <>
      <PageHeader
        kicker="Equipe"
        title="Profissionais"
        subtitle="Quem atende na clínica, em que área e quais serviços faz. Cada profissional tem a própria agenda."
        action={<Button onClick={abrirCriar} className="w-full lg:w-auto">Novo profissional</Button>}
      />

      {error && <ErrorBanner message={error} />}

      {loading ? (
        <Spinner />
      ) : !profissionais || profissionais.length === 0 ? (
        <EmptyState message="Nenhum profissional cadastrado. Adicione o primeiro com o botão acima." />
      ) : (
        <>
          <CardList>
            {profissionais.map((p) => (
              <CardItem key={p.id}>
                <div className="flex items-center gap-2">
                  <CorPonto cor={p.cor} />
                  <CardLabel>{p.nome}</CardLabel>
                  {!p.ativo && <span className={microLabel}>inativo</span>}
                </div>
                <CardDetail>{p.geral ? 'Agenda padrão da clínica' : especialidadesTexto(p)}</CardDetail>
                <CardDetail className="truncate">{resumoServicos(p, servicos)}</CardDetail>
                <CardActions>
                  <Button variant="ghost" size="sm" onClick={() => abrirEditar(p)}>
                    Editar
                  </Button>
                </CardActions>
              </CardItem>
            ))}
          </CardList>
          <div className="hidden border-t border-hairline md:block">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft">
                  <th className="py-3 pr-8 font-medium">Nome</th>
                  <th className="py-3 pr-8 font-medium">Especialidades</th>
                  <th className="py-3 pr-8 font-medium">Serviços</th>
                  <th className="py-3 pr-8 font-medium">Situação</th>
                  <th className="py-3 text-right font-medium">Ações</th>
                </tr>
              </thead>
              <tbody>
                {profissionais.map((p) => (
                  <tr key={p.id} className="border-t border-hairline transition-colors duration-150 ease-in-out hover:bg-paper">
                    <td className="py-4 pr-8 font-medium text-ink">
                      <span className="inline-flex items-center gap-2">
                        <CorPonto cor={p.cor} />
                        {p.nome}
                      </span>
                    </td>
                    <td className="py-4 pr-8 text-ink-soft">{p.geral ? 'Agenda padrão da clínica' : especialidadesTexto(p)}</td>
                    <td className="max-w-xs truncate py-4 pr-8 text-ink-soft">{resumoServicos(p, servicos)}</td>
                    <td className="py-4 pr-8 text-ink-soft">{p.ativo ? 'Ativo' : 'Inativo'}</td>
                    <td className="py-4 text-right whitespace-nowrap">
                      <Button variant="ghost" size="sm" onClick={() => abrirEditar(p)}>
                        Editar
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <section className="mt-14">
        <div className="mb-4 border-b border-hairline pb-3">
          <h2 className="font-display text-2xl font-medium text-ink">Especialidades</h2>
          <p className="mt-1 text-sm text-ink-soft">As áreas da clínica, usadas para agrupar a equipe.</p>
        </div>
        {erroEspecialidades && <ErrorBanner message={erroEspecialidades} />}
        <div className="flex flex-col divide-y divide-hairline border-y border-hairline">
          {(especialidades ?? []).map((e) => (
            <div key={e.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <span className="text-sm text-ink">{e.nome}</span>
              <span className="flex gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setRenomeando(e)
                    setNomeEspecialidade(e.nome)
                    setEspecialidadeErro('')
                  }}
                >
                  Renomear
                </Button>
                <Button variant="dangerText" size="sm" onClick={() => setExcluindo(e)}>
                  Excluir
                </Button>
              </span>
            </div>
          ))}
        </div>
        <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <TextField
              label="Nova especialidade"
              value={novaEspecialidade}
              maxLength={80}
              onChange={(e) => setNovaEspecialidade(e.target.value)}
              placeholder="Ex.: Pilates"
            />
          </div>
          <Button variant="secondary" size="sm" className="self-start sm:mb-1" onClick={criarEspecialidade} disabled={!novaEspecialidade.trim()}>
            Adicionar
          </Button>
        </div>
        {especialidadeErro && !renomeando && <p className="mt-3 text-sm text-red-600">{especialidadeErro}</p>}
      </section>

      <Modal
        open={modalAberto}
        title={editando ? 'Editar profissional' : 'Novo profissional'}
        onClose={() => setModalAberto(false)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex max-h-[70vh] flex-col gap-5 overflow-y-auto pr-1">
          <TextField
            label="Nome *"
            value={form.nome}
            maxLength={120}
            disabled={editando?.geral}
            onChange={(e) => setForm({ ...form, nome: e.target.value })}
          />
          {editando?.geral && (
            <p className="-mt-3 text-sm text-ink-soft">
              O "Geral" guarda o expediente padrão da clínica e os atendimentos antigos. O nome não muda, mas ele pode
              ser desativado quando a equipe estiver cadastrada.
            </p>
          )}

          <div className="flex flex-wrap items-end gap-6">
            <label className="flex flex-col">
              <span className={`mb-1 ${microLabel}`}>Cor na agenda</span>
              <input
                type="color"
                value={form.cor ?? COR_PADRAO}
                onChange={(e) => setForm({ ...form, cor: e.target.value.toUpperCase() })}
                className="h-10 w-16 cursor-pointer border border-hairline bg-transparent"
              />
            </label>
            <Button
              variant={form.ativo ? 'secondary' : 'ghost'}
              size="sm"
              onClick={() => setForm({ ...form, ativo: !form.ativo })}
            >
              {form.ativo ? 'Ativo' : 'Inativo'}
            </Button>
          </div>

          <div>
            <span className={`mb-2 block ${microLabel}`}>Especialidades</span>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {(especialidades ?? []).map((e) => (
                <Marcavel
                  key={e.id}
                  rotulo={e.nome}
                  marcado={form.especialidadeIds.includes(e.id)}
                  onClick={() => setForm({ ...form, especialidadeIds: alternar(form.especialidadeIds, e.id) })}
                />
              ))}
            </div>
          </div>

          <div>
            <span className={`mb-1 block ${microLabel}`}>Serviços que faz</span>
            <p className="mb-2 text-sm text-ink-soft">
              Um serviço sem nenhum profissional marcado pode ser feito por qualquer um. Ao marcar, ele passa a ser só
              dos profissionais marcados.
            </p>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {(servicos ?? []).map((s) => (
                <Marcavel
                  key={s.id}
                  rotulo={s.nome}
                  marcado={form.servicoIds.includes(s.id)}
                  onClick={() => setForm({ ...form, servicoIds: alternar(form.servicoIds, s.id) })}
                />
              ))}
            </div>
          </div>

          {!editando && (
            <p className="text-sm text-ink-soft">
              O expediente começa igual ao padrão da clínica e pode ser ajustado em Agenda › Horários.
            </p>
          )}

          {formErro && <p className="text-sm text-red-600">{formErro}</p>}

          <div className="mt-2 flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setModalAberto(false)} disabled={salvando}>
              Cancelar
            </Button>
            <Button onClick={salvar} disabled={salvando}>
              {salvando ? 'Salvando...' : 'Salvar'}
            </Button>
          </div>
        </div>
      </Modal>

      <Modal open={renomeando !== null} title="Renomear especialidade" onClose={() => setRenomeando(null)}>
        <div className="flex flex-col gap-5">
          <TextField
            label="Nome *"
            value={nomeEspecialidade}
            maxLength={80}
            onChange={(e) => setNomeEspecialidade(e.target.value)}
          />
          {especialidadeErro && <p className="text-sm text-red-600">{especialidadeErro}</p>}
          <div className="flex justify-end gap-3">
            <Button variant="ghost" onClick={() => setRenomeando(null)}>
              Cancelar
            </Button>
            <Button onClick={salvarRenomear} disabled={!nomeEspecialidade.trim()}>
              Salvar
            </Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={excluindo !== null}
        title="Excluir especialidade"
        message={`Excluir "${excluindo?.nome}"? Os profissionais continuam cadastrados, só perdem essa marcação.`}
        confirmLabel="Excluir"
        error={especialidadeErro}
        pending={salvando}
        pendingLabel="Excluindo..."
        onConfirm={confirmarExcluir}
        onCancel={() => {
          if (salvando) return
          setExcluindo(null)
          setEspecialidadeErro('')
        }}
      />
    </>
  )
}
