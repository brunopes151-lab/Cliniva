import { http } from './http'
import type {
  AtualizarUsuarioInput,
  CriarUsuarioInput,
  Especialidade,
  Profissional,
  ProfissionalInput,
  SenhaTemporaria,
  UsuarioClinica,
  UsuarioCriado,
} from '@/types'

export const profissionaisApi = {
  listar: () => http.get<Profissional[]>('/profissionais'),
  criar: (input: ProfissionalInput) => http.post<Profissional>('/profissionais', input),
  atualizar: (id: string, input: ProfissionalInput) => http.put<Profissional>(`/profissionais/${id}`, input),
}

export const especialidadesApi = {
  listar: () => http.get<Especialidade[]>('/especialidades'),
  criar: (nome: string) => http.post<Especialidade>('/especialidades', { nome }),
  renomear: (id: string, nome: string) => http.put<Especialidade>(`/especialidades/${id}`, { nome }),
  excluir: (id: string) => http.delete<void>(`/especialidades/${id}`),
}

export const usuariosApi = {
  listar: () => http.get<UsuarioClinica[]>('/usuarios'),
  criar: (input: CriarUsuarioInput) => http.post<UsuarioCriado>('/usuarios', input),
  atualizar: (id: string, input: AtualizarUsuarioInput) => http.put<UsuarioClinica>(`/usuarios/${id}`, input),
  resetarSenha: (id: string) => http.post<SenhaTemporaria>(`/usuarios/${id}/reset-senha`, {}),
}
