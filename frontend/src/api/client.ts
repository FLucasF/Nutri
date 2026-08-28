import type {
  Agendamento,
  AgendamentoRequest,
  AlimentoDetalhe,
  AlimentoResumo,
  Apuracao,
  Avaliacao,
  AvaliacaoRequest,
  Composicao,
  DiaDaAgenda,
  Evolucao,
  FonteDeDados,
  Lancamento,
  LancamentoRequest,
  Medida,
  Pagina,
  Paciente,
  PacienteResumo,
  PlanoPublico,
  PlanoRequest,
  PlanoResponse,
  PlanoResumo,
  PorcaoCalculada,
  ProtocoloInfo,
  Orientacao,
  OrientacaoDoPlano,
  ExameResultado,
  ParametroExame,
  FormularioPublico,
  Questionario,
  Recibo,
  RespostaDeQuestionario,
  UsuarioDaConta,
  SerieDeExame,
  SolicitacaoDeExame,
  Receita,
  ReceitaRequest,
  ReceitaResumo,
  ResultadoImportacao,
  SituacaoAtendimento,
  SituacaoLancamento,
  TipoAtendimentoInfo,
  TipoLancamento,
  TokenResponse,
} from "./types";

const BASE = "/api";
const CHAVE_TOKEN = "nutriplan.token";

/** Erro da API já traduzido para o que a interface precisa mostrar. */
export class ErroApi extends Error {
  constructor(
    readonly status: number,
    mensagem: string,
    readonly campos?: { campo: string; mensagem: string }[],
  ) {
    super(mensagem);
    this.name = "ErroApi";
  }

  /** Erro de validação tem detalhe por campo; os demais, só a mensagem. */
  get ehValidacao() {
    return this.status === 400 && !!this.campos?.length;
  }
}

export function lerToken(): string | null {
  try {
    return localStorage.getItem(CHAVE_TOKEN);
  } catch {
    return null;
  }
}

export function gravarToken(token: string | null) {
  try {
    if (token) localStorage.setItem(CHAVE_TOKEN, token);
    else localStorage.removeItem(CHAVE_TOKEN);
  } catch {
    /* navegação privada: a sessão vale só enquanto a aba viver */
  }
}

type Opcoes = {
  metodo?: "GET" | "POST" | "PUT" | "DELETE";
  corpo?: unknown;
  /** Requisição pública: não envia credencial nem redireciona ao expirar. */
  semAutenticacao?: boolean;
  formData?: FormData;
};

let aoExpirarSessao: (() => void) | null = null;

/** Registrado pelo contexto de autenticação para reagir a um 401. */
export function definirTratamentoDeSessaoExpirada(callback: () => void) {
  aoExpirarSessao = callback;
}

/**
 * Busca um arquivo em rota autenticada e devolve a URL temporária dele.
 *
 * Quem chama é responsável por liberar a URL depois de usá-la; sem isso o
 * arquivo fica retido em memória enquanto a aba viver.
 */
async function baixar(caminho: string): Promise<{ url: string; nome: string }> {
  const token = lerToken();
  const resposta = await fetch(`${BASE}${caminho}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!resposta.ok) {
    if (resposta.status === 401) {
      aoExpirarSessao?.();
    }
    throw new ErroApi(resposta.status, "Não foi possível gerar o arquivo.");
  }

  const disposicao = resposta.headers.get("Content-Disposition") ?? "";
  const nome = /filename="?([^";]+)"?/.exec(disposicao)?.[1] ?? "arquivo.pdf";
  return { url: URL.createObjectURL(await resposta.blob()), nome };
}

async function requisitar<T>(caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const cabecalhos: Record<string, string> = {};
  const token = lerToken();

  if (!opcoes.semAutenticacao && token) {
    cabecalhos.Authorization = `Bearer ${token}`;
  }
  if (opcoes.corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
  }

  const resposta = await fetch(`${BASE}${caminho}`, {
    method: opcoes.metodo ?? "GET",
    headers: cabecalhos,
    body: opcoes.formData ?? (opcoes.corpo !== undefined ? JSON.stringify(opcoes.corpo) : undefined),
  });

  if (resposta.status === 204) {
    return undefined as T;
  }

  const texto = await resposta.text();
  const dados = texto ? JSON.parse(texto) : null;

  if (!resposta.ok) {
    if (resposta.status === 401 && !opcoes.semAutenticacao) {
      aoExpirarSessao?.();
    }
    throw new ErroApi(
      resposta.status,
      dados?.mensagem ?? "Não foi possível completar a operação.",
      dados?.campos,
    );
  }
  return dados as T;
}

function query(params: Record<string, string | number | boolean | undefined>) {
  const busca = new URLSearchParams();
  Object.entries(params).forEach(([chave, valor]) => {
    if (valor !== undefined && valor !== "") busca.set(chave, String(valor));
  });
  const texto = busca.toString();
  return texto ? `?${texto}` : "";
}

export const api = {
  // ------------------------------------------------------------- autenticação
  login: (email: string, senha: string) =>
    requisitar<TokenResponse>("/auth/login", {
      metodo: "POST",
      corpo: { email, senha },
      semAutenticacao: true,
    }),

  cadastrar: (dados: { nome: string; email: string; senha: string; crn?: string; telefone?: string }) =>
    requisitar<TokenResponse>("/auth/cadastro", {
      metodo: "POST",
      corpo: dados,
      semAutenticacao: true,
    }),

  eu: () => requisitar<TokenResponse["usuario"]>("/auth/eu"),

  // ------------------------------------------------------------------ pacientes
  pacientes: {
    listar: (params: { termo?: string; ativo?: boolean; page?: number; size?: number }) =>
      requisitar<Pagina<PacienteResumo>>(`/pacientes${query(params)}`),

    buscar: (id: number) => requisitar<Paciente>(`/pacientes/${id}`),

    criar: (dados: Partial<Paciente>) =>
      requisitar<Paciente>("/pacientes", { metodo: "POST", corpo: dados }),

    atualizar: (id: number, dados: Partial<Paciente>) =>
      requisitar<Paciente>(`/pacientes/${id}`, { metodo: "PUT", corpo: dados }),

    inativar: (id: number) => requisitar<void>(`/pacientes/${id}`, { metodo: "DELETE" }),

    reativar: (id: number) =>
      requisitar<Paciente>(`/pacientes/${id}/reativar`, { metodo: "POST" }),

    importar: (arquivo: File, separador = ",") => {
      const dados = new FormData();
      dados.append("arquivo", arquivo);
      dados.append("separador", separador);
      return requisitar<ResultadoImportacao>("/pacientes/importar", {
        metodo: "POST",
        formData: dados,
      });
    },
  },

  // ------------------------------------------------------------------ alimentos
  alimentos: {
    buscar: (params: { termo?: string; grupo?: string; fonte?: FonteDeDados; page?: number; size?: number }) =>
      requisitar<Pagina<AlimentoResumo>>(`/alimentos${query(params)}`),

    grupos: () => requisitar<string[]>("/alimentos/grupos"),

    detalhar: (id: number) => requisitar<AlimentoDetalhe>(`/alimentos/${id}`),

    /**
     * Busca pelo código de barras. Devolve lista porque o código não é chave:
     * o mesmo EAN aparece mais de uma vez na base colaborativa, e o
     * consultório pode ter cadastrado o próprio produto com ele.
     */
    porCodigoDeBarras: (codigo: string) =>
      requisitar<AlimentoDetalhe[]>(`/alimentos/codigo-barras/${encodeURIComponent(codigo)}`),

    porcao: (id: number, quantidade: number, medidaId?: number) =>
      requisitar<PorcaoCalculada>(`/alimentos/${id}/porcao${query({ quantidade, medidaId })}`),

    criar: (dados: {
      descricao: string;
      grupo?: string;
      marca?: string;
      codigoBarras?: string;
      composicao: Composicao;
      medidas?: unknown[];
    }) =>
      requisitar<AlimentoDetalhe>("/alimentos", { metodo: "POST", corpo: dados }),

    atualizar: (id: number, dados: unknown) =>
      requisitar<AlimentoDetalhe>(`/alimentos/${id}`, { metodo: "PUT", corpo: dados }),

    adicionarMedida: (id: number, dados: { descricao: string; gramas: number; padrao: boolean }) =>
      requisitar<Medida>(`/alimentos/${id}/medidas`, { metodo: "POST", corpo: dados }),

    removerMedida: (id: number, medidaId: number) =>
      requisitar<void>(`/alimentos/${id}/medidas/${medidaId}`, { metodo: "DELETE" }),

    importar: (arquivo: File, fonte?: FonteDeDados, separador = ",") => {
      const dados = new FormData();
      dados.append("arquivo", arquivo);
      if (fonte) dados.append("fonte", fonte);
      dados.append("separador", separador);
      return requisitar<ResultadoImportacao>("/alimentos/importar", {
        metodo: "POST",
        formData: dados,
      });
    },
  },

  // ---------------------------------------------------------------- prescrições
  prescricoes: {
    listar: (params: { pacienteId?: number; modelo?: boolean; termo?: string; page?: number; size?: number }) =>
      requisitar<Pagina<PlanoResumo>>(`/prescricoes${query(params)}`),

    detalhar: (id: number) => requisitar<PlanoResponse>(`/prescricoes/${id}`),

    criar: (dados: PlanoRequest) =>
      requisitar<PlanoResponse>("/prescricoes", { metodo: "POST", corpo: dados }),

    atualizar: (id: number, dados: PlanoRequest) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}`, { metodo: "PUT", corpo: dados }),

    publicar: (id: number) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}/publicar`, { metodo: "POST" }),

    encerrar: (id: number) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}/encerrar`, { metodo: "POST" }),

    voltarParaRascunho: (id: number) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}/rascunho`, { metodo: "POST" }),

    regerarLink: (id: number) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}/regerar-link`, { metodo: "POST" }),

    duplicar: (id: number, pacienteId?: number, titulo?: string) =>
      requisitar<PlanoResponse>(`/prescricoes/${id}/duplicar${query({ pacienteId, titulo })}`, {
        metodo: "POST",
      }),

    remover: (id: number) => requisitar<void>(`/prescricoes/${id}`, { metodo: "DELETE" }),

    /**
     * Baixa o plano em PDF.
     *
     * Vai por fetch, e não por link direto: a rota exige autenticação, e uma
     * âncora comum não carrega o cabeçalho do token.
     */
    pdf: (id: number) => baixar(`/prescricoes/${id}/pdf`),
  },

  // ------------------------------------------------------------------ receitas
  receitas: {
    listar: (params: { termo?: string; page?: number; size?: number }) =>
      requisitar<Pagina<ReceitaResumo>>(`/receitas${query(params)}`),

    detalhar: (id: number) => requisitar<Receita>(`/receitas/${id}`),

    criar: (dados: ReceitaRequest) =>
      requisitar<Receita>("/receitas", { metodo: "POST", corpo: dados }),

    atualizar: (id: number, dados: ReceitaRequest) =>
      requisitar<Receita>(`/receitas/${id}`, { metodo: "PUT", corpo: dados }),

    remover: (id: number) => requisitar<void>(`/receitas/${id}`, { metodo: "DELETE" }),
  },

  // ---------------------------------------------------------- questionários
  questionarios: {
    listar: () => requisitar<Questionario[]>("/questionarios"),

    detalhar: (id: number) => requisitar<Questionario>(`/questionarios/${id}`),

    duplicar: (id: number) =>
      requisitar<Questionario>(`/questionarios/${id}/duplicar`, { metodo: "POST" }),

    remover: (id: number) => requisitar<void>(`/questionarios/${id}`, { metodo: "DELETE" }),

    doPaciente: (pacienteId: number) =>
      requisitar<RespostaDeQuestionario[]>(`/pacientes/${pacienteId}/questionarios`),

    enviar: (pacienteId: number, dados: { questionarioId: number; agendamentoId?: number }) =>
      requisitar<RespostaDeQuestionario>(`/pacientes/${pacienteId}/questionarios`, {
        metodo: "POST",
        corpo: dados,
      }),

    cancelarEnvio: (id: number) =>
      requisitar<void>(`/questionarios/envios/${id}`, { metodo: "DELETE" }),

    /** Formulário como o paciente o vê: sem credencial nenhuma. */
    formulario: (identificador: string) =>
      requisitar<FormularioPublico>(`/publico/questionarios/${identificador}`, {
        semAutenticacao: true,
      }),

    responder: (identificador: string, respostas: { perguntaId: number; valor: string }[]) =>
      requisitar<void>(`/publico/questionarios/${identificador}`, {
        metodo: "POST",
        corpo: { respostas },
        semAutenticacao: true,
      }),
  },

  // ---------------------------------------------------------------- equipe
  usuarios: {
    listar: () => requisitar<UsuarioDaConta[]>("/usuarios"),

    criarSecretaria: (dados: {
      nome: string;
      email: string;
      senhaInicial: string;
      telefone?: string;
    }) => requisitar<UsuarioDaConta>("/usuarios", { metodo: "POST", corpo: dados }),

    inativar: (id: number) => requisitar<void>(`/usuarios/${id}`, { metodo: "DELETE" }),

    reativar: (id: number) =>
      requisitar<UsuarioDaConta>(`/usuarios/${id}/reativar`, { metodo: "POST" }),
  },

  // ---------------------------------------------------------------- acesso
  acesso: {
    /**
     * Pede o link de redefinição.
     *
     * Responde igual exista o e-mail ou não: um endpoint que dissesse
     * "e-mail não encontrado" permitiria varrer endereços e descobrir quem
     * tem conta.
     */
    recuperarSenha: (email: string) =>
      requisitar<void>("/auth/recuperar-senha", {
        metodo: "POST",
        corpo: { email },
        semAutenticacao: true,
      }),

    redefinirSenha: (token: string, novaSenha: string) =>
      requisitar<void>("/auth/redefinir-senha", {
        metodo: "POST",
        corpo: { token, novaSenha },
        semAutenticacao: true,
      }),
  },

  // ------------------------------------------------------------------ exames
  exames: {
    parametros: () => requisitar<ParametroExame[]>("/exames/parametros"),

    criarParametro: (dados: {
      nome: string;
      unidadePadrao: string;
      grupo?: string;
      minimo?: number;
      maximo?: number;
    }) => requisitar<ParametroExame>("/exames/parametros", { metodo: "POST", corpo: dados }),

    doPaciente: (pacienteId: number) =>
      requisitar<ExameResultado[]>(`/pacientes/${pacienteId}/exames`),

    registrar: (
      pacienteId: number,
      dados: {
        parametroId: number;
        dataColeta: string;
        valor?: number;
        unidade?: string;
        observacao?: string;
      },
    ) =>
      requisitar<ExameResultado>(`/pacientes/${pacienteId}/exames`, {
        metodo: "POST",
        corpo: dados,
      }),

    serie: (pacienteId: number, parametroId: number) =>
      requisitar<SerieDeExame>(`/pacientes/${pacienteId}/exames/serie/${parametroId}`),

    remover: (id: number) => requisitar<void>(`/exames/${id}`, { metodo: "DELETE" }),

    anexarLaudo: (id: number, arquivo: File) => {
      const dados = new FormData();
      dados.append("arquivo", arquivo);
      return requisitar<void>(`/exames/${id}/laudo`, { metodo: "POST", formData: dados });
    },

    laudo: (id: number) => baixar(`/exames/${id}/laudo`),

    solicitacoes: (pacienteId: number) =>
      requisitar<SolicitacaoDeExame[]>(`/pacientes/${pacienteId}/solicitacoes-de-exame`),

    solicitar: (
      pacienteId: number,
      dados: { data: string; parametroIds: number[]; observacao?: string },
    ) =>
      requisitar<SolicitacaoDeExame>(`/pacientes/${pacienteId}/solicitacoes-de-exame`, {
        metodo: "POST",
        corpo: dados,
      }),
  },

  // ------------------------------------------------------------- orientações
  orientacoes: {
    listar: (params: { termo?: string; page?: number; size?: number }) =>
      requisitar<Pagina<Orientacao>>(`/orientacoes${query(params)}`),

    detalhar: (id: number) => requisitar<Orientacao>(`/orientacoes/${id}`),

    criar: (dados: { titulo: string; corpo: string }) =>
      requisitar<Orientacao>("/orientacoes", { metodo: "POST", corpo: dados }),

    duplicar: (id: number) =>
      requisitar<Orientacao>(`/orientacoes/${id}/duplicar`, { metodo: "POST" }),

    atualizar: (id: number, dados: { titulo: string; corpo: string }) =>
      requisitar<Orientacao>(`/orientacoes/${id}`, { metodo: "PUT", corpo: dados }),

    remover: (id: number) => requisitar<void>(`/orientacoes/${id}`, { metodo: "DELETE" }),

    enviarImagem: (id: number, arquivo: File) => {
      const dados = new FormData();
      dados.append("arquivo", arquivo);
      return requisitar<void>(`/orientacoes/${id}/imagem`, {
        metodo: "POST",
        formData: dados,
      });
    },

    /**
     * Baixa a figura e devolve uma URL temporária para o `src`.
     *
     * A rota exige credencial e a tag `img` não manda cabeçalho — por isso o
     * arquivo vem por `fetch`. Quem chama libera a URL ao desmontar.
     */
    imagem: (id: number) => baixar(`/orientacoes/${id}/imagem`),

    imagemNoPlano: (planoId: number, anexoId: number) =>
      baixar(`/prescricoes/${planoId}/orientacoes/${anexoId}/imagem`),

    doPlano: (planoId: number) =>
      requisitar<OrientacaoDoPlano[]>(`/prescricoes/${planoId}/orientacoes`),

    anexar: (planoId: number, dados: { orientacaoId?: number; titulo?: string; corpo?: string }) =>
      requisitar<OrientacaoDoPlano>(`/prescricoes/${planoId}/orientacoes`, {
        metodo: "POST",
        corpo: dados,
      }),

    editarNoPlano: (planoId: number, anexoId: number, dados: { titulo: string; corpo: string }) =>
      requisitar<OrientacaoDoPlano>(`/prescricoes/${planoId}/orientacoes/${anexoId}`, {
        metodo: "PUT",
        corpo: dados,
      }),

    desanexar: (planoId: number, anexoId: number) =>
      requisitar<void>(`/prescricoes/${planoId}/orientacoes/${anexoId}`, { metodo: "DELETE" }),
  },

  // -------------------------------------------------------------- antropometria
  antropometria: {
    protocolos: () => requisitar<ProtocoloInfo[]>("/antropometria/protocolos"),

    listar: (pacienteId: number) =>
      requisitar<Avaliacao[]>(`/pacientes/${pacienteId}/avaliacoes`),

    evolucao: (pacienteId: number) =>
      requisitar<Evolucao>(`/pacientes/${pacienteId}/evolucao`),

    criar: (pacienteId: number, dados: AvaliacaoRequest) =>
      requisitar<Avaliacao>(`/pacientes/${pacienteId}/avaliacoes`, {
        metodo: "POST",
        corpo: dados,
      }),

    detalhar: (id: number) => requisitar<Avaliacao>(`/avaliacoes/${id}`),

    atualizar: (id: number, dados: AvaliacaoRequest) =>
      requisitar<Avaliacao>(`/avaliacoes/${id}`, { metodo: "PUT", corpo: dados }),

    remover: (id: number) => requisitar<void>(`/avaliacoes/${id}`, { metodo: "DELETE" }),
  },

  // --------------------------------------------------------------------- agenda
  agenda: {
    tipos: () => requisitar<TipoAtendimentoInfo[]>("/agenda/tipos"),

    doDia: (data: string) => requisitar<DiaDaAgenda>(`/agenda/dia${query({ data })}`),

    naFaixa: (de: string, ate: string, situacao?: SituacaoAtendimento) =>
      requisitar<Agendamento[]>(`/agenda${query({ de, ate, situacao })}`),

    doPaciente: (pacienteId: number) =>
      requisitar<Agendamento[]>(`/agenda/paciente/${pacienteId}`),

    agendar: (dados: AgendamentoRequest) =>
      requisitar<Agendamento>("/agenda", { metodo: "POST", corpo: dados }),

    remarcar: (id: number, dados: AgendamentoRequest) =>
      requisitar<Agendamento>(`/agenda/${id}`, { metodo: "PUT", corpo: dados }),

    mudarSituacao: (id: number, situacao: SituacaoAtendimento, motivo?: string) =>
      requisitar<Agendamento>(`/agenda/${id}/situacao`, {
        metodo: "POST",
        corpo: { situacao, motivo },
      }),

    remover: (id: number) => requisitar<void>(`/agenda/${id}`, { metodo: "DELETE" }),

    /**
     * Assinatura da agenda em calendário externo.
     *
     * `token` ausente quer dizer que o endereço ainda não foi criado — não é
     * erro, é o estado inicial.
     */
    assinatura: () => requisitar<{ token?: string }>("/agenda/assinatura"),

    gerarAssinatura: () =>
      requisitar<{ token: string }>("/agenda/assinatura", { metodo: "POST" }),

    revogarAssinatura: () =>
      requisitar<void>("/agenda/assinatura", { metodo: "DELETE" }),
  },

  // ----------------------------------------------------------------- financeiro
  financeiro: {
    listar: (params: {
      de?: string;
      ate?: string;
      tipo?: TipoLancamento;
      situacao?: SituacaoLancamento;
      pacienteId?: number;
      page?: number;
      size?: number;
    }) => requisitar<Pagina<Lancamento>>(`/financeiro/lancamentos${query(params)}`),

    vencidos: (referencia?: string) =>
      requisitar<Lancamento[]>(`/financeiro/vencidos${query({ referencia })}`),

    apurar: (de: string, ate: string) =>
      requisitar<Apuracao>(`/financeiro/apuracao${query({ de, ate })}`),

    criar: (dados: LancamentoRequest) =>
      requisitar<Lancamento>("/financeiro/lancamentos", { metodo: "POST", corpo: dados }),

    atualizar: (id: number, dados: LancamentoRequest) =>
      requisitar<Lancamento>(`/financeiro/lancamentos/${id}`, { metodo: "PUT", corpo: dados }),

    pagar: (id: number, dataPagamento?: string) =>
      requisitar<Lancamento>(`/financeiro/lancamentos/${id}/pagar`, {
        metodo: "POST",
        corpo: { dataPagamento },
      }),

    estornar: (id: number) =>
      requisitar<Lancamento>(`/financeiro/lancamentos/${id}/estornar`, { metodo: "POST" }),

    cancelar: (id: number) =>
      requisitar<Lancamento>(`/financeiro/lancamentos/${id}/cancelar`, { metodo: "POST" }),

    recibo: (id: number) => requisitar<Recibo>(`/financeiro/lancamentos/${id}/recibo`),

    remover: (id: number) =>
      requisitar<void>(`/financeiro/lancamentos/${id}`, { metodo: "DELETE" }),
  },

  /** Plano aberto pelo paciente: sem credencial. */
  planoPublico: (identificador: string) =>
    requisitar<PlanoPublico>(`/publico/planos/${identificador}`, { semAutenticacao: true }),
};
