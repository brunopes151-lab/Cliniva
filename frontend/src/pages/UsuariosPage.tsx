import { useState } from 'react'
import { profissionaisApi, usuariosApi } from '@/api/equipeApi'
import { Button } from '@/components/ui/Button'
import { CardActions, CardDetail, CardItem, CardLabel, CardList } from '@/components/ui/CardList'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorBanner } from '@/components/ui/ErrorBanner'
import { Modal } from '@/components/ui/Modal'
import { PageHeader } from '@/components/ui/PageHeader'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { TextField } from '@/components/ui/TextField'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/hooks/useAuth'
import { PAPEL_LABEL } from '@/types'
import type { Papel, Profissional, UsuarioClinica } from '@/types'

const PAPEIS_DA_CLINICA: Papel[] = ['OWNER', 'RECEPCAO', 'PROFISSIONAL']

const DESCRICAO_PAPEL: Record<Papel, string> = {
  ADMIN: '',
  OWNER: 'Vê e configura tudo: equipe, acessos, serviços, horários e marca.',
  RECEPCAO: 'Agenda de todos, pacientes, atendimentos e estoque. Não mexe em configurações.',
  PROFISSIONAL: 'Só a própria agenda e os pacientes que atende.',
}

interface FormUsuario {
  nome: string
  email: string
  papel: Papel
  profissionalId: string
  ativo: boolean
}

const formVazio: FormUsuario = { nome: '', email: '', papel: 'RECEPCAO', profissionalId: '', ativo: true }

interface SenhaGerada {
  email: string
  senha: string
}

export function UsuariosPage() {
  const { usuario: logado } = useAuth()
  const { data: usuarios, loading, error, refetch } = useApi<UsuarioClinica[]>(() => usuariosApi.listar())
  const { data: profissionais } = useApi<Profissional[]>(() => profissionaisApi.listar())

  const [modalAberto, setModalAberto] = useState(false)
  const [editando, setEditando] = useState<UsuarioClinica | null>(null)
  const [form, setForm] = useState<FormUsuario>(formVazio)
  const [formErro, setFormErro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [senhaGerada, setSenhaGerada] = useState<SenhaGerada | null>(null)
  const [aviso, setAviso] = useState('')

  const [resetando, setResetando] = useState<UsuarioClinica | null>(null)
  const [resetErro, setResetErro] = useState('')

  // Um login por profissional: só oferece quem ainda não tem acesso (ou o do
  // próprio usuário em edição).
  const profissionaisLivres = (profissionais ?? []).filter(
    (p) =>
      !p.geral &&
      (p.id === editando?.profissionalId || !(usuarios ?? []).some((u) => u.profissionalId === p.id)),
  )

  const abrirCriar = () => {
    setEditando(null)
    setForm(formVazio)
    setFormErro('')
    setModalAberto(true)
  }

  const abrirEditar = (u: UsuarioClinica) => {
    setEditando(u)
    setForm({ nome: u.nome ?? '', email: u.email, papel: u.papel, profissionalId: u.profissionalId ?? '', ativo: u.ativo })
    setFormErro('')
    setModalAberto(true)
  }

  const salvar = async () => {
    if (!form.nome.trim()) {
      setFormErro('Informe o nome.')
      return
    }
    if (!editando && !form.email.trim()) {
      setFormErro('Informe o e-mail de acesso.')
      return
    }
    if (form.papel === 'PROFISSIONAL' && !form.profissionalId) {
      setFormErro('Escolha qual profissional usa este acesso.')
      return
    }
    // Recepção não atende; o vínculo só vale para profissional e administrador.
    const profissionalId = form.papel === 'RECEPCAO' ? null : form.profissionalId || null
    setSalvando(true)
    setFormErro('')
    setAviso('')
    try {
      if (editando) {
        await usuariosApi.atualizar(editando.id, {
          nome: form.nome.trim(),
          papel: form.papel,
          profissionalId,
          ativo: form.ativo,
        })
      } else {
        const criado = await usuariosApi.criar({
          nome: form.nome.trim(),
          email: form.email.trim(),
          papel: form.papel,
          profissionalId,
        })
        if (criado.senhaTemporaria) {
          setSenhaGerada({ email: criado.usuario.email, senha: criado.senhaTemporaria })
        } else {
          setAviso(`Acesso de ${criado.usuario.email} cadastrado. A pessoa entra com a conta que já tem.`)
        }
      }
      setModalAberto(false)
      refetch()
    } catch (err) {
      setFormErro(err instanceof Error ? err.message : 'Falha ao salvar o acesso.')
    } finally {
      setSalvando(false)
    }
  }

  const confirmarReset = async () => {
    if (!resetando) return
    setSalvando(true)
    setResetErro('')
    try {
      const resposta = await usuariosApi.resetarSenha(resetando.id)
      setResetando(null)
      setSenhaGerada({ email: resposta.email, senha: resposta.senhaTemporaria })
    } catch (err) {
      setResetErro(err instanceof Error ? err.message : 'Falha ao gerar nova senha.')
    } finally {
      setSalvando(false)
    }
  }

  const ehVoce = (u: UsuarioClinica) => u.id === logado?.id
  const vinculo = (u: UsuarioClinica) => u.profissionalNome ?? (u.papel === 'PROFISSIONAL' ? '—' : 'Não atende')

  return (
    <>
      <PageHeader
        kicker="Equipe"
        title="Usuários e acessos"
        subtitle="Quem entra no sistema e o que cada um vê. Cada profissional que atende tem o próprio login."
        action={<Button onClick={abrirCriar} className="w-full lg:w-auto">Novo acesso</Button>}
      />

      {error && <ErrorBanner message={error} />}
      {aviso && <div className="mb-6 border border-accent/40 bg-accent/10 px-4 py-3 text-sm text-ink">{aviso}</div>}

      {loading ? (
        <Spinner />
      ) : !usuarios || usuarios.length === 0 ? (
        <EmptyState message="Nenhum acesso cadastrado." />
      ) : (
        <>
          <CardList>
            {usuarios.map((u) => (
              <CardItem key={u.id}>
                <CardLabel>
                  {u.nome || u.email}
                  {ehVoce(u) ? ' (você)' : ''}
                </CardLabel>
                <CardDetail className="truncate">{u.email}</CardDetail>
                <CardDetail>
                  {PAPEL_LABEL[u.papel]} · {vinculo(u)}
                  {u.ativo ? '' : ' · inativo'}
                </CardDetail>
                <CardActions>
                  <Button variant="ghost" size="sm" onClick={() => abrirEditar(u)}>
                    Editar
                  </Button>
                  {!ehVoce(u) && (
                    <Button variant="ghost" size="sm" onClick={() => setResetando(u)}>
                      Nova senha
                    </Button>
                  )}
                </CardActions>
              </CardItem>
            ))}
          </CardList>
          <div className="hidden border-t border-hairline md:block">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[11px] font-medium uppercase tracking-[0.18em] text-ink-soft">
                  <th className="py-3 pr-8 font-medium">Nome</th>
                  <th className="py-3 pr-8 font-medium">E-mail</th>
                  <th className="py-3 pr-8 font-medium">Perfil</th>
                  <th className="py-3 pr-8 font-medium">Profissional</th>
                  <th className="py-3 pr-8 font-medium">Situação</th>
                  <th className="py-3 text-right font-medium">Ações</th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((u) => (
                  <tr key={u.id} className="border-t border-hairline transition-colors duration-150 ease-in-out hover:bg-paper">
                    <td className="py-4 pr-8 font-medium text-ink">
                      {u.nome || '—'}
                      {ehVoce(u) ? ' (você)' : ''}
                    </td>
                    <td className="max-w-[14rem] truncate py-4 pr-8 text-ink-soft">{u.email}</td>
                    <td className="py-4 pr-8 text-ink-soft">{PAPEL_LABEL[u.papel]}</td>
                    <td className="py-4 pr-8 text-ink-soft">{vinculo(u)}</td>
                    <td className="py-4 pr-8 text-ink-soft">{u.ativo ? 'Ativo' : 'Inativo'}</td>
                    <td className="py-4 text-right whitespace-nowrap">
                      <Button variant="ghost" size="sm" onClick={() => abrirEditar(u)}>
                        Editar
                      </Button>
                      {!ehVoce(u) && (
                        <Button variant="ghost" size="sm" className="ml-4" onClick={() => setResetando(u)}>
                          Nova senha
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <Modal
        open={modalAberto}
        title={editando ? 'Editar acesso' : 'Novo acesso'}
        onClose={() => setModalAberto(false)}
        bloqueiaFechamento={salvando}
      >
        <div className="flex max-h-[70vh] flex-col gap-5 overflow-y-auto pr-1">
          <TextField
            label="Nome *"
            value={form.nome}
            maxLength={120}
            onChange={(e) => setForm({ ...form, nome: e.target.value })}
          />
          <TextField
            label="E-mail de acesso *"
            type="email"
            value={form.email}
            disabled={editando !== null}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <div>
            <Select
              label="Perfil *"
              value={form.papel}
              disabled={editando !== null && ehVoce(editando)}
              onChange={(e) => setForm({ ...form, papel: e.target.value as Papel })}
            >
              {PAPEIS_DA_CLINICA.map((p) => (
                <option key={p} value={p}>
                  {PAPEL_LABEL[p]}
                </option>
              ))}
            </Select>
            <p className="mt-2 text-sm text-ink-soft">{DESCRICAO_PAPEL[form.papel]}</p>
          </div>

          {form.papel !== 'RECEPCAO' && (
            <Select
              label={form.papel === 'PROFISSIONAL' ? 'Profissional *' : 'Também atende como'}
              value={form.profissionalId}
              onChange={(e) => setForm({ ...form, profissionalId: e.target.value })}
            >
              <option value="">{form.papel === 'PROFISSIONAL' ? 'Selecione...' : 'Não atende'}</option>
              {profissionaisLivres.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome}
                </option>
              ))}
            </Select>
          )}
          {form.papel === 'PROFISSIONAL' && profissionaisLivres.length === 0 && (
            <p className="-mt-3 text-sm text-ink-soft">
              Todos os profissionais já têm acesso. Cadastre a pessoa primeiro em Profissionais.
            </p>
          )}

          {editando && !ehVoce(editando) && (
            <Button
              variant={form.ativo ? 'secondary' : 'ghost'}
              size="sm"
              className="self-start"
              onClick={() => setForm({ ...form, ativo: !form.ativo })}
            >
              {form.ativo ? 'Acesso ativo' : 'Acesso bloqueado'}
            </Button>
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

      <Modal open={senhaGerada !== null} title="Senha temporária" onClose={() => setSenhaGerada(null)}>
        <div className="flex flex-col gap-4">
          <p className="text-sm text-ink-soft">
            Entregue esta senha para <span className="font-medium text-ink">{senhaGerada?.email}</span>. Ela aparece só
            agora; no primeiro acesso a pessoa deve trocá-la em “Trocar senha”.
          </p>
          <p className="select-all break-all border border-hairline bg-ivory px-4 py-3 font-mono text-lg text-ink">
            {senhaGerada?.senha}
          </p>
          <div className="flex justify-end">
            <Button onClick={() => setSenhaGerada(null)}>Entendi</Button>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={resetando !== null}
        title="Gerar nova senha"
        message={`Gerar uma senha temporária para ${resetando?.email}? A senha atual deixa de funcionar.`}
        confirmLabel="Gerar senha"
        error={resetErro}
        pending={salvando}
        pendingLabel="Gerando..."
        onConfirm={confirmarReset}
        onCancel={() => {
          if (salvando) return
          setResetando(null)
          setResetErro('')
        }}
      />
    </>
  )
}
