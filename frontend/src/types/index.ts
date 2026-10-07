export type ClienteStatus = 'PROSPECT' | 'ATIVO' | 'INATIVO'

export type OrigemCliente = 'INDICACAO' | 'INSTAGRAM' | 'GOOGLE' | 'PASSOU_NA_RUA' | 'ONLINE'

export type CanalPreferido = 'WHATSAPP' | 'INSTAGRAM' | 'EMAIL' | 'LIGACAO'

export interface Cliente {
  id: string
  nome: string
  email: string | null
  telefone: string
  dataNascimento: string | null
  status: ClienteStatus
  origem: OrigemCliente | null
  canalPreferido: CanalPreferido | null
  preferencias: string | null
  observacoes: string | null
}

export interface ClienteInput {
  nome: string
  email: string
  telefone: string
  dataNascimento?: string
  status?: ClienteStatus | ''
  origem?: OrigemCliente | ''
  canalPreferido?: CanalPreferido | ''
  preferencias?: string
  observacoes?: string
}

export interface ClienteNota {
  id: string
  texto: string
  criadaEm: string
}

export interface ClienteHistorico {
  cliente: Cliente
  totalAtendimentos: number
  atendimentosConcluidos: number
  gastoTotal: number
  ticketMedio: number | null
  ultimaVisita: string | null
  atendimentos: Atendimento[]
}

export interface Servico {
  id: string
  nome: string
  descricao: string | null
  valor: number
  duracaoMinutos: number
}

export interface Item {
  id: string
  nome: string
  quantidadeEmEstoque: number
}

export type StatusAtendimento = 'AGENDADO' | 'CONCLUIDO' | 'CANCELADO'

export interface AtendimentoResumo {
  id: string
  clienteId: string
  nomeCliente: string
  telefoneCliente: string
  dataAtendimento: string
  status: StatusAtendimento
  valorTotal: number
  profissionalId: string
  profissionalNome: string
}

export interface AtendimentoItem {
  itemId: string
  nomeItem: string
  quantidadeUsada: number
}

export interface AtendimentoServico {
  servicoId: string
  nomeServico: string
  valorCobrado: number
  itensUsados: AtendimentoItem[]
}

export interface Atendimento {
  id: string
  clienteId: string
  nomeCliente: string
  dataAtendimento: string
  dataCriacao: string
  status: StatusAtendimento
  valorTotal: number
  servicos: AtendimentoServico[]
  profissionalId: string
  profissionalNome: string
}

export interface ServicoInput {
  nome: string
  descricao: string
  valor: number
  duracaoMinutos: number
}

export interface ItemInput {
  nome: string
  quantidadeEmEstoque: number
}

export type TipoMovimentacao = 'ENTRADA' | 'SAIDA'

export interface MovimentacaoEstoqueInput {
  tipo: TipoMovimentacao
  quantidade: number
}

export interface AtendimentoFiltros {
  status?: StatusAtendimento
  clienteId?: string
  profissionalId?: string
  dataInicio?: string
  dataFim?: string
}

export interface AtendimentoItemExtraInput {
  itemId: string
  quantidade: number
}

export interface AtendimentoServicoInput {
  servicoId: string
  itensExtras: AtendimentoItemExtraInput[]
}

export interface AtendimentoInput {
  clienteId: string
  profissionalId: string
  dataAtendimento: string
  servicos: AtendimentoServicoInput[]
}

export interface AtendimentoUpdate {
  clienteId: string
  dataAtendimento: string
  profissionalId?: string
}

export interface AgendaItem {
  id: string
  clienteId: string
  clienteNome: string
  clienteTelefone: string
  inicio: string
  fim: string
  duracaoMinutos: number
  status: StatusAtendimento
  valorTotal: number
  servicos: string[]
  profissionalId: string
  profissionalNome: string
  profissionalCor: string | null
}

export interface HorarioAtendimento {
  diaSemana: number
  abertura: string
  fechamento: string
  ativo: boolean
}

export interface DisponibilidadeDia {
  data: string
  duracaoMinutos: number
  horarios: string[]
}

export interface AgendaLink {
  slug: string
  caminho: string
}

export interface ServicoPublico {
  id: string
  nome: string
  descricao: string | null
  valor: number
  duracaoMinutos: number
  clinica: string
}

export interface BookingInput {
  servicoId: string
  dataHora: string
  nome: string
  telefone: string
  email?: string
  profissionalId?: string
}

export interface BookingResult {
  id: string
  dataAtendimento: string
  duracaoMinutos: number
  servico: string
  clinica: string
  cliente: string
  profissional: string
}
export type Papel = 'ADMIN' | 'OWNER' | 'RECEPCAO' | 'PROFISSIONAL'

export const PAPEL_LABEL: Record<Papel, string> = {
  ADMIN: 'Suporte da plataforma',
  OWNER: 'Administrador',
  RECEPCAO: 'Recepção',
  PROFISSIONAL: 'Profissional',
}

export interface Especialidade {
  id: string
  nome: string
}

export interface Profissional {
  id: string
  nome: string
  cor: string | null
  ativo: boolean
  geral: boolean
  especialidades: Especialidade[]
  /** Vazio = atende qualquer serviço sem vínculo. */
  servicoIds: string[]
}

export interface ProfissionalInput {
  nome: string
  cor: string | null
  ativo: boolean
  especialidadeIds: string[]
  servicoIds: string[]
}

export interface ProfissionalPublico {
  id: string
  nome: string
  especialidades: string[]
}

export interface UsuarioClinica {
  id: string
  nome: string | null
  email: string
  papel: Papel
  ativo: boolean
  profissionalId: string | null
  profissionalNome: string | null
}

export interface CriarUsuarioInput {
  nome: string
  email: string
  papel: Papel
  profissionalId: string | null
}

export interface AtualizarUsuarioInput {
  nome: string
  papel: Papel
  profissionalId: string | null
  ativo: boolean
}

export interface UsuarioCriado {
  usuario: UsuarioClinica
  senhaTemporaria: string | null
}

export interface SenhaTemporaria {
  email: string
  senhaTemporaria: string
}
