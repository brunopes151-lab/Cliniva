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
  serieId: string | null
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

export type FrequenciaSerie = 'DIARIA' | 'SEMANAL' | 'QUINZENAL' | 'MENSAL'

export const FREQUENCIA_LABEL: Record<FrequenciaSerie, string> = {
  DIARIA: 'Todo dia (seg. a sex.)',
  SEMANAL: 'Toda semana',
  QUINZENAL: 'A cada 15 dias',
  MENSAL: 'Todo mês',
}

export interface SerieInput {
  clienteId: string
  profissionalId: string
  servicos: AtendimentoServicoInput[]
  inicio: string
  frequencia: FrequenciaSerie
  incluiSabado: boolean
  dataFim?: string | null
  quantidade?: number | null
}

export interface OcorrenciaSerie {
  dataHora: string
  disponivel: boolean
  motivo: string | null
}

export interface PreviaSerie {
  ocorrencias: OcorrenciaSerie[]
  disponiveis: number
  puladas: number
}

export interface SerieCriada {
  serieId: string
  criados: unknown[]
  puladas: OcorrenciaSerie[]
}

export interface Pacote {
  id: string
  nome: string
  servicoId: string
  servicoNome: string
  sessoes: number
  validadeDias: number
  preco: number
  ativo: boolean
}

export interface PacoteInput {
  nome: string
  servicoId: string
  sessoes: number
  validadeDias: number
  preco: number
  ativo?: boolean
}

export type SituacaoPacote = 'ATIVO' | 'ESGOTADO' | 'VENCIDO' | 'CANCELADO'

export const SITUACAO_PACOTE_LABEL: Record<SituacaoPacote, string> = {
  ATIVO: 'Ativo',
  ESGOTADO: 'Esgotado',
  VENCIDO: 'Vencido',
  CANCELADO: 'Cancelado',
}

export interface MovimentoPacote {
  tipo: 'BAIXA' | 'ESTORNO'
  criadoEm: string
  atendimentoId: string | null
  dataAtendimento: string | null
}

export interface PacoteCliente {
  id: string
  pacoteId: string
  nome: string
  servicoId: string
  servicoNome: string
  sessoesTotal: number
  saldo: number
  dataCompra: string
  dataValidade: string
  valorPago: number
  situacao: SituacaoPacote
  movimentos: MovimentoPacote[]
}

export interface VenderPacoteInput {
  pacoteId: string
  dataCompra?: string
  valorPago?: number
}

export type AreaFicha = 'ESTETICA' | 'FISIOTERAPIA' | 'GERAL'
export type TipoRegistro = 'ANAMNESE' | 'AVALIACAO_INICIAL' | 'PLANO_TERAPEUTICO' | 'EVOLUCAO' | 'REAVALIACAO'
export type TipoCampo = 'TEXTO' | 'TEXTO_LONGO' | 'NUMERO' | 'SIM_NAO' | 'ESCALA' | 'OPCOES' | 'DATA'

export const AREA_FICHA_LABEL: Record<AreaFicha, string> = {
  ESTETICA: 'Estética',
  FISIOTERAPIA: 'Fisioterapia',
  GERAL: 'Geral',
}

export const TIPO_REGISTRO_LABEL: Record<TipoRegistro, string> = {
  ANAMNESE: 'Anamnese',
  AVALIACAO_INICIAL: 'Avaliação inicial',
  PLANO_TERAPEUTICO: 'Plano terapêutico',
  EVOLUCAO: 'Evolução',
  REAVALIACAO: 'Reavaliação',
}

export const TIPO_CAMPO_LABEL: Record<TipoCampo, string> = {
  TEXTO: 'Texto curto',
  TEXTO_LONGO: 'Texto longo',
  NUMERO: 'Número',
  SIM_NAO: 'Sim ou não',
  ESCALA: 'Escala de 0 a 10',
  OPCOES: 'Escolha entre opções',
  DATA: 'Data',
}

export interface CampoFicha {
  id: string
  rotulo: string
  tipo: TipoCampo
  obrigatorio: boolean
  opcoes: string[] | null
}

export interface ModeloFicha {
  id: string
  familiaId: string
  versao: number
  nome: string
  area: AreaFicha
  tipo: TipoRegistro
  campos: CampoFicha[]
  ativo: boolean
}

export interface ModeloFichaInput {
  nome: string
  area: AreaFicha
  tipo: TipoRegistro
  campos: CampoFicha[]
}

export type RespostaFicha = string | number | boolean
export type ConteudoFicha = Record<string, RespostaFicha>

export interface VersaoRegistro {
  numero: number
  criadoEm: string
  autorNome: string
  motivo: string | null
  modelo: ModeloFicha
  conteudo: ConteudoFicha
}

export interface RegistroClinico {
  id: string
  tipo: TipoRegistro
  criadoEm: string
  profissionalId: string
  profissionalNome: string
  atendimentoId: string | null
  dataAtendimento: string | null
  versoes: number
  podeCorrigir: boolean
  atual: VersaoRegistro
}

// --- LGPD ---

export interface TermoConsentimento {
  id: string
  versao: number
  texto: string
  vigenteDesde: string
  publicadoPor: string | null
}

export interface ConsentimentoPaciente {
  id: string
  termoId: string
  versaoTermo: number
  termoAtual: boolean
  aceitoEm: string
  registradoPor: string
  vigente: boolean
  revogadoEm: string | null
  revogadoPor: string | null
  motivoRevogacao: string | null
}

export interface SituacaoConsentimento {
  termoAtual: TermoConsentimento
  /** Há um aceite não revogado (de qualquer versão). */
  vigente: boolean
  aceitouVersaoAtual: boolean
  consentimentos: ConsentimentoPaciente[]
}

export type AcaoAuditoria =
  | 'VER_PRONTUARIO'
  | 'VER_HISTORICO'
  | 'CRIAR_REGISTRO'
  | 'CORRIGIR_REGISTRO'
  | 'EXPORTAR_DADOS'
  | 'REGISTRAR_CONSENTIMENTO'
  | 'REVOGAR_CONSENTIMENTO'

export const ACAO_AUDITORIA_LABEL: Record<AcaoAuditoria, string> = {
  VER_PRONTUARIO: 'Abriu o prontuário',
  VER_HISTORICO: 'Viu as versões de um registro',
  CRIAR_REGISTRO: 'Fez um registro',
  CORRIGIR_REGISTRO: 'Corrigiu um registro',
  EXPORTAR_DADOS: 'Exportou os dados',
  REGISTRAR_CONSENTIMENTO: 'Registrou o aceite do termo',
  REVOGAR_CONSENTIMENTO: 'Registrou a revogação do termo',
}

export interface AcessoAuditoria {
  em: string
  usuario: string
  papel: Papel
  modoSuporte: boolean
  acao: AcaoAuditoria
  registroId: string | null
  ip: string | null
}

export interface ExportacaoPaciente {
  geradoEm: string
  geradoPor: string
  clinica: string
  cadastro: Cliente
  anotacoes: ClienteNota[]
  atendimentos: Atendimento[]
  pacotes: PacoteCliente[]
  prontuario: { registro: RegistroClinico; versoes: VersaoRegistro[] }[]
  consentimentos: ConsentimentoPaciente[]
  termosAceitos: TermoConsentimento[]
}
