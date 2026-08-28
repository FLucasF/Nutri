/** Contratos da API, espelhando os DTOs do backend. */

export type Perfil = "NUTRICIONISTA" | "SECRETARIA" | "PACIENTE" | "ADMIN";
export type Plano = "EXPERIMENTAL" | "GRADUACAO" | "PREMIUM" | "BLACK";
export type Sexo = "FEMININO" | "MASCULINO";

export type FonteDeDados =
  | "TACO"
  | "TBCA"
  | "IBGE"
  | "OPEN_FOOD_FACTS"
  | "FABRICANTE"
  | "PERSONALIZADO"
  | "RECEITA";

export type MetodoPrescricao = "ALIMENTOS" | "EQUIVALENTES" | "QUALITATIVO";
export type StatusPlano = "RASCUNHO" | "ATIVO" | "ENCERRADO";

export interface UsuarioResumo {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  contaId: number;
  plano: Plano;
}

export interface TokenResponse {
  token: string;
  tipo: string;
  expiraEmSegundos: number;
  usuario: UsuarioResumo;
}

export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PacienteResumo {
  id: number;
  nome: string;
  email?: string;
  idade?: number;
  ativo: boolean;
}

export interface Paciente {
  id: number;
  nome: string;
  email?: string;
  telefone?: string;
  dataNascimento?: string;
  idade?: number;
  sexo?: Sexo;
  cpf?: string;
  profissao?: string;
  objetivo?: string;
  observacoes?: string;
  ativo: boolean;
  temAcessoAoApp: boolean;
  criadoEm: string;
}

/**
 * Composição nutricional. Chave ausente significa nutriente não determinado
 * na fonte — deve aparecer como "não informado", nunca como zero.
 */
export type Composicao = Record<string, number | undefined>;

export interface AlimentoResumo {
  id: number;
  descricao: string;
  grupo?: string;
  fonte: FonteDeDados;
  marca?: string;
  energiaKcal?: number;
  proteinaG?: number;
  carboidratoG?: number;
  lipideosG?: number;
  basePublica: boolean;
}

export interface Medida {
  id: number;
  descricao: string;
  gramas: number;
  padrao: boolean;
  doAcervoBase: boolean;
  editavel: boolean;
}

export interface AlimentoDetalhe {
  id: number;
  descricao: string;
  grupo?: string;
  fonte: FonteDeDados;
  fonteDescricao: string;
  codigoFonte?: string;
  /** EAN do produto industrializado. Ausente nas tabelas de referência. */
  codigoBarras?: string;
  marca?: string;
  basePublica: boolean;
  editavel: boolean;
  composicao: Composicao;
  medidas: Medida[];
}

export interface PorcaoCalculada {
  alimentoId: number;
  descricao: string;
  gramas: number;
  medidaUsada: string;
  composicao: Composicao;
}

export interface ResultadoImportacao {
  importados: number;
  ignorados: number;
  avisos: string[];
}

export interface Distribuicao {
  proteinaPct: number;
  carboidratoPct: number;
  lipideoPct: number;
  energiaCalculadaKcal: number;
}

export interface Total {
  composicao: Composicao;
  itensNoCalculo: number;
  itensForaDoCalculo: number;
  nutrientesIncompletos: string[];
  nutrientesSemDado: string[];
  confiavel: boolean;
  distribuicao?: Distribuicao;
  adequacaoEnergeticaPct?: number;
}

export interface Equivalente {
  id?: number;
  alimentoId?: number;
  descricao: string;
  porcao: string;
  gramas?: number;
}

export interface Item {
  id?: number;
  alimentoId?: number;
  medidaId?: number;
  descricao: string;
  porcao: string;
  quantidade?: number;
  gramas?: number;
  ordem?: number;
  observacao?: string;
  equivalentes: Equivalente[];
}

export interface RefeicaoResponse {
  id?: number;
  nome: string;
  horario?: string;
  ordem?: number;
  observacao?: string;
  itens: Item[];
  total: Total;
}

export interface PlanoResponse {
  id: number;
  titulo: string;
  pacienteId?: number;
  pacienteNome?: string;
  metodo: MetodoPrescricao;
  metodoDescricao: string;
  status: StatusPlano;
  statusDescricao: string;
  identificadorPublico: string;
  vigenciaInicio?: string;
  vigenciaFim?: string;
  orientacoes?: string;
  observacoesInternas?: string;
  metaEnergiaKcal?: number;
  modelo: boolean;
  refeicoes: RefeicaoResponse[];
  totalDoDia: Total;
  criadoEm: string;
  atualizadoEm?: string;
}

export interface PlanoResumo {
  id: number;
  titulo: string;
  pacienteId?: number;
  pacienteNome?: string;
  metodo: MetodoPrescricao;
  status: StatusPlano;
  vigenciaInicio?: string;
  vigenciaFim?: string;
  modelo: boolean;
  refeicoes: number;
  itens: number;
  energiaKcal?: number;
  atualizadoEm?: string;
}

// ---- corpo de escrita ----

export interface EquivalenteRequest {
  alimentoId?: number;
  medidaId?: number;
  descricao: string;
  quantidade?: number;
}

export interface ItemRequest {
  alimentoId?: number;
  medidaId?: number;
  descricao?: string;
  quantidade?: number;
  observacao?: string;
  equivalentes?: EquivalenteRequest[];
}

export interface RefeicaoRequest {
  nome: string;
  horario?: string;
  observacao?: string;
  itens: ItemRequest[];
}

export interface PlanoRequest {
  titulo: string;
  pacienteId?: number;
  metodo: MetodoPrescricao;
  vigenciaInicio?: string;
  vigenciaFim?: string;
  orientacoes?: string;
  observacoesInternas?: string;
  metaEnergiaKcal?: number;
  modelo: boolean;
  refeicoes: RefeicaoRequest[];
}

// ---- visão do paciente ----

export interface ItemPublico {
  descricao: string;
  /** Texto pronto da medida caseira, ex.: "4 colher de sopa". */
  porcao: string;
  /** Peso da porção. Ausente quando o item foi prescrito sem peso definido. */
  pesoGramas?: number;
  observacao?: string;
  substituicoes: { descricao: string; porcao: string }[];
}

export interface RefeicaoPublica {
  nome: string;
  horario?: string;
  observacao?: string;
  itens: ItemPublico[];
}

export interface OrientacaoPublica {
  id: number;
  titulo: string;
  corpo: string;
  /** Endereço da figura, já pronto para o `src`. Ausente quando não há figura. */
  imagem?: string;
}

export interface PlanoPublico {
  titulo: string;
  pacienteNome?: string;
  nutricionistaNome?: string;
  nutricionistaCrn?: string;
  consultorioNome?: string;
  corPrimaria?: string;
  logoUrl?: string;
  metodo: MetodoPrescricao;
  vigente: boolean;
  encerrado: boolean;
  vigenciaInicio?: string;
  vigenciaFim?: string;
  orientacoes?: string;
  /** Orientações anexadas, no texto congelado no momento do anexo. */
  orientacoesAnexadas: OrientacaoPublica[];
  refeicoes: RefeicaoPublica[];
  resumo: {
    energiaKcal?: number;
    proteinaG?: number;
    carboidratoG?: number;
    lipideosG?: number;
    refeicoes: number;
  };
}

// ============================================================ antropometria

export type ProtocoloComposicao =
  | "FAULKNER"
  | "POLLOCK_3"
  | "POLLOCK_7"
  | "DURNIN_WOMERSLEY";

export type EquacaoGasto = "MIFFLIN_ST_JEOR" | "HARRIS_BENEDICT";

export type ClassificacaoImc =
  | "BAIXO_PESO"
  | "EUTROFIA"
  | "SOBREPESO"
  | "OBESIDADE_I"
  | "OBESIDADE_II"
  | "OBESIDADE_III";

export type RiscoCardiometabolico = "BAIXO" | "MODERADO" | "ALTO";

/**
 * Valor derivado com o motivo quando não pôde ser calculado.
 * Só o nulo obrigaria a interface a adivinhar se o dado falta porque a medida
 * não foi feita ou porque a regra impede o cálculo.
 */
export interface Derivado<T> {
  valor?: T;
  indisponivelPorque?: string;
}

export interface ComposicaoCorporal {
  protocolo: ProtocoloComposicao;
  protocoloDescricao: string;
  percentualGordura?: number;
  massaGordaKg?: number;
  massaMagraKg?: number;
}

export interface GastoEnergetico {
  equacao: EquacaoGasto;
  equacaoDescricao: string;
  fatorAtividade?: number;
  basalKcal?: number;
  totalKcal?: number;
}

export interface Avaliacao {
  id: number;
  pacienteId: number;
  pacienteNome: string;
  data: string;
  pesoKg?: number;
  alturaCm?: number;
  dobras: Record<string, number>;
  circunferencias: Record<string, number>;
  imc?: number;
  classificacaoImc: Derivado<ClassificacaoImc>;
  relacaoCinturaQuadril?: number;
  riscoCardiometabolico: Derivado<RiscoCardiometabolico>;
  composicao?: ComposicaoCorporal;
  gastoEnergetico?: GastoEnergetico;
  /** Presente quando o paciente tem até 19 anos. */
  crescimentoInfantil?: Derivado<CrescimentoInfantil>;
  /** Presente quando a avaliação informa semana gestacional. */
  gestacao?: Derivado<Gestacao>;
  observacoes?: string;
  criadoEm: string;
}

export type IndicadorDeCrescimento = "IMC_PARA_IDADE" | "ESTATURA_PARA_IDADE";

export interface IndicadorInfantil {
  indicador: IndicadorDeCrescimento;
  indicadorDescricao: string;
  escoreZ?: number;
  classificacao?: string;
  classificacaoDescricao?: string;
  exigeAtencao: boolean;
  /** Qual curva da OMS foi usada: são referências distintas. */
  referencia: string;
}

export interface CrescimentoInfantil {
  idadeEmMeses: number;
  indicadores: IndicadorInfantil[];
}

export interface Gestacao {
  semanaGestacional: number;
  pesoPreGestacionalKg: number;
  imcPreGestacional: number;
  faixa: string;
  faixaDescricao: string;
  ganhoAteAgora: number;
  esperadoMin: number;
  esperadoMax: number;
  situacao?: "ABAIXO" | "ADEQUADO" | "ACIMA";
  situacaoDescricao?: string;
  ganhoTotalRecomendadoMin: number;
  ganhoTotalRecomendadoMax: number;
}

export interface AvaliacaoRequest {
  data: string;
  pesoKg?: number;
  alturaCm?: number;
  dobras?: Record<string, number>;
  circunferencias?: Record<string, number>;
  protocoloComposicao?: ProtocoloComposicao;
  equacaoGasto?: EquacaoGasto;
  fatorAtividade?: number;
  observacoes?: string;
  semanaGestacional?: number;
  pesoPreGestacionalKg?: number;
}

export interface Variacao {
  medida: string;
  rotulo: string;
  atual?: number;
  anterior?: number;
  diferenca?: number;
  comparavel: boolean;
  observacao?: string;
}

export interface PontoDaEvolucao {
  avaliacaoId: number;
  data: string;
  pesoKg?: number;
  imc?: number;
  percentualGordura?: number;
  protocolo?: ProtocoloComposicao;
  variacoesFrenteAAnterior: Variacao[];
  variacoesFrenteAPrimeira: Variacao[];
}

export interface Evolucao {
  pacienteId: number;
  pacienteNome: string;
  totalDeAvaliacoes: number;
  pontos: PontoDaEvolucao[];
}

export interface ProtocoloInfo {
  protocolo: ProtocoloComposicao;
  descricao: string;
  exigeSexo: boolean;
  exigeIdade: boolean;
  dobrasFemininas: string[];
  dobrasMasculinas: string[];
}

// ==================================================================== agenda

export type TipoAtendimento =
  | "PRIMEIRA_CONSULTA"
  | "RETORNO"
  | "AVALIACAO"
  | "ORIENTACAO"
  | "OUTRO";

export type SituacaoAtendimento =
  | "AGENDADO"
  | "CONFIRMADO"
  | "REALIZADO"
  | "FALTOU"
  | "CANCELADO";

export interface Agendamento {
  id: number;
  pacienteId: number;
  pacienteNome?: string;
  inicio: string;
  fim: string;
  duracaoMinutos: number;
  tipo: TipoAtendimento;
  tipoDescricao: string;
  situacao: SituacaoAtendimento;
  situacaoDescricao: string;
  transicoesPermitidas: SituacaoAtendimento[];
  observacao?: string;
  motivoDesfecho?: string;
}

export interface AgendamentoRequest {
  pacienteId: number;
  inicio: string;
  duracaoMinutos: number;
  tipo: TipoAtendimento;
  observacao?: string;
}

export interface DiaDaAgenda {
  data: string;
  totalDeAtendimentos: number;
  realizados: number;
  faltas: number;
  atendimentos: Agendamento[];
}

export interface TipoAtendimentoInfo {
  tipo: TipoAtendimento;
  descricao: string;
  duracaoSugeridaMinutos: number;
}

// ================================================================ financeiro

export type TipoLancamento = "RECEITA" | "DESPESA";
export type SituacaoLancamento = "PENDENTE" | "PAGO" | "CANCELADO";

export interface Lancamento {
  id: number;
  tipo: TipoLancamento;
  tipoDescricao: string;
  situacao: SituacaoLancamento;
  situacaoDescricao: string;
  valor: number;
  competencia: string;
  vencimento?: string;
  dataPagamento?: string;
  categoria: string;
  formaPagamento?: string;
  descricao?: string;
  pacienteId?: number;
  pacienteNome?: string;
  agendamentoId?: number;
  vencido: boolean;
}

export interface LancamentoRequest {
  tipo: TipoLancamento;
  valor: number;
  competencia: string;
  vencimento?: string;
  categoria: string;
  formaPagamento?: string;
  descricao?: string;
  pacienteId?: number;
  agendamentoId?: number;
}

export interface TotalPorCategoria {
  categoria: string;
  tipo: TipoLancamento;
  total: number;
  lancamentos: number;
}

export interface Apuracao {
  de: string;
  ate: string;
  totalRecebido: number;
  totalAReceber: number;
  despesasPagas: number;
  despesasAPagar: number;
  resultadoEfetivado: number;
  resultadoPrevisto: number;
  lancamentos: number;
  porCategoria: TotalPorCategoria[];
}

export interface Recibo {
  lancamentoId: number;
  consultorioNome?: string;
  profissionalNome?: string;
  profissionalCrn?: string;
  pagadorNome?: string;
  valor: number;
  valorPorExtenso: string;
  dataPagamento: string;
  referente: string;
  emitidoEm: string;
}

// ---- receitas ----

export interface IngredienteReceita {
  id?: number;
  alimentoId: number;
  descricao: string;
  fonteDescricao: string;
  medidaId?: number;
  /** Texto pronto: "2 colheres de sopa" ou "150 g". */
  quantidade: string;
  gramas: number;
}

export interface Receita {
  id: number;
  nome: string;
  grupo?: string;
  modoPreparo?: string;
  /** Peso final usado no cálculo. */
  rendimentoGramas: number;
  /** Verdadeiro quando o peso final não foi informado e a soma foi presumida. */
  rendimentoEstimado: boolean;
  pesoDosIngredientes: number;
  porcoes?: number;
  gramasPorPorcao?: number;
  composicaoPor100g: Composicao;
  composicaoDaPorcao?: Composicao;
  /** Nutrientes somados de apenas parte dos ingredientes: são piso, não total. */
  nutrientesIncompletos: string[];
  ingredientes: IngredienteReceita[];
}

export interface ReceitaResumo {
  id: number;
  nome: string;
  grupo?: string;
  totalDeIngredientes: number;
  rendimentoGramas?: number;
  porcoes?: number;
  energiaKcalPor100g?: number;
}

export interface IngredienteRequest {
  alimentoId: number;
  medidaId?: number;
  quantidade: number;
}

export interface ReceitaRequest {
  nome: string;
  grupo?: string;
  rendimentoGramas?: number;
  porcoes?: number;
  modoPreparo?: string;
  ingredientes: IngredienteRequest[];
}

// ---- orientações nutricionais ----

export interface Orientacao {
  id: number;
  titulo: string;
  corpo: string;
  /** Modelo que acompanha o sistema: ponto de partida, não editável. */
  modeloDoSistema: boolean;
  editavel: boolean;
  temImagem: boolean;
  imagemNome?: string;
}

export interface OrientacaoDoPlano {
  id: number;
  /** Procedência na biblioteca. Ausente quando o texto foi escrito na hora. */
  orientacaoId?: number;
  titulo: string;
  corpo: string;
  ordem: number;
  /** A cópia entregue neste plano, e não a figura da biblioteca. */
  temImagem: boolean;
}

// ---- exames laboratoriais ----

export type ClassificacaoDoExame = "ABAIXO" | "NORMAL" | "ACIMA";

export interface FaixaDeReferencia {
  sexo?: Sexo;
  idadeMin?: number;
  idadeMax?: number;
  minimo?: number;
  maximo?: number;
  texto: string;
}

export interface ParametroExame {
  id: number;
  nome: string;
  unidadePadrao: string;
  grupo?: string;
  doCatalogoDoSistema: boolean;
  editavel: boolean;
  faixas: FaixaDeReferencia[];
}

export interface ExameResultado {
  id: number;
  parametroId: number;
  parametro: string;
  grupo?: string;
  dataColeta: string;
  /** Ausente significa pedido e ainda não determinado — nunca zero. */
  valor?: number;
  unidade: string;
  classificacao?: ClassificacaoDoExame;
  classificacaoDescricao?: string;
  /** A faixa usada na entrada, e não a cadastrada hoje. */
  referencia?: string;
  observacao?: string;
  temLaudo: boolean;
  laudoNome?: string;
}

export interface PontoDaSerie {
  dataColeta: string;
  valor?: number;
  unidade: string;
  classificacao?: ClassificacaoDoExame;
  variacao?: number;
}

export interface SerieDeExame {
  parametroId: number;
  parametro: string;
  unidade: string;
  pontos: PontoDaSerie[];
  /** Verdadeiro quando há coletas em unidades diferentes: não são comparáveis. */
  unidadesMisturadas: boolean;
}

export interface SolicitacaoDeExame {
  id: number;
  data: string;
  observacao?: string;
  exames: string[];
}

// ---- equipe do consultório ----

export interface UsuarioDaConta {
  id: number;
  nome: string;
  email: string;
  perfil: Perfil;
  perfilDescricao: string;
  ativo: boolean;
  criadoEm?: string;
}

// ---- questionários ----

export type TipoDePergunta = "TEXTO" | "NUMERO" | "ESCOLHA_UNICA" | "MULTIPLA";

export interface OpcaoDeResposta {
  rotulo: string;
  pontos?: number;
}

export interface PerguntaDoQuestionario {
  id: number;
  enunciado: string;
  tipo: TipoDePergunta;
  tipoDescricao: string;
  obrigatoria: boolean;
  ordem: number;
  ajuda?: string;
  opcoes: OpcaoDeResposta[];
}

export interface Questionario {
  id: number;
  nome: string;
  descricao?: string;
  instrumento?: string;
  versao?: string;
  pontuavel: boolean;
  faixaDeCorte?: string;
  versaoModelo: number;
  modeloDoSistema: boolean;
  editavel: boolean;
  perguntas: PerguntaDoQuestionario[];
}

export interface ItemRespondido {
  pergunta: string;
  valor?: string;
  pontos?: number;
}

export interface RespostaDeQuestionario {
  id: number;
  questionarioId: number;
  questionario: string;
  /** A edição do formulário que o paciente viu. */
  versaoModelo: number;
  identificadorPublico: string;
  enviadoEm: string;
  respondidoEm?: string;
  pendente: boolean;
  escore?: number;
  classificacao?: string;
  itens: ItemRespondido[];
}

export interface FormularioPublico {
  consultorio?: string;
  titulo: string;
  descricao?: string;
  jaRespondido: boolean;
  perguntas: PerguntaDoQuestionario[];
}
