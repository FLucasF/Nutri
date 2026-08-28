import { ErroApi } from "./client";

/**
 * Traduz uma falha para o que a pessoa precisa saber.
 *
 * Antes cada tela tinha a sua frase de reserva — "Falha ao salvar.", "Não foi
 * possível concluir a operação." — e havia trinta delas. Todas dizem que algo
 * deu errado e nenhuma diz o quê, nem o que fazer em seguida.
 *
 * A regra aqui é: a mensagem nomeia **o que se tentava fazer**, **o que
 * impediu** e, quando existe, **o próximo passo**. Erro de regra de negócio e
 * de validação já chegam prontos do servidor e são repassados como estão — quem
 * escreveu a regra sabe explicá-la melhor que uma frase genérica na tela.
 *
 * @param acao o que se tentava fazer, em infinitivo e do ponto de vista de quem
 *             usa: "salvar o paciente", "abrir a agenda". Entra na frase.
 */
export function explicarErro(e: unknown, acao: string): string {
  if (e instanceof ErroApi) {
    switch (e.status) {
      case 400:
      case 404:
      case 405:
      case 422:
        // O servidor já explicou: regra de negócio, campo inválido, endereço
        // que não existe. Reescrever aqui só afastaria a mensagem da regra.
        return e.message;

      case 403:
        return `Seu perfil não permite ${acao}. Peça a quem administra o consultório.`;

      case 409:
        return `Não deu para ${acao}: já existe um registro com esses dados.`;

      case 413:
        return "O arquivo é grande demais. Envie um menor.";

      case 500:
      case 502:
      case 503:
        return `O servidor não conseguiu ${acao}. Tente de novo em instantes; se persistir, o problema é do servidor e não do que você preencheu.`;

      default:
        return e.message || `Não deu para ${acao}.`;
    }
  }

  if (e instanceof TypeError) {
    // fetch só rejeita com TypeError quando a requisição não chegou a sair.
    return `Sem resposta do servidor ao tentar ${acao}. Verifique sua conexão e tente de novo — nada foi alterado.`;
  }

  return `Não deu para ${acao}.`;
}

/** Os erros por campo de uma falha de validação, prontos para grudar no campo. */
export function errosPorCampo(e: unknown): Record<string, string> {
  if (!(e instanceof ErroApi) || !e.campos?.length) return {};
  const mapa: Record<string, string> = {};
  for (const c of e.campos) {
    // O primeiro erro de cada campo basta: dois avisos no mesmo campo competem
    // pela atenção e o segundo raramente acrescenta.
    if (!mapa[c.campo]) mapa[c.campo] = c.mensagem;
  }
  return mapa;
}

/**
 * A hora de agora, como a régua do dia escreve.
 *
 * "Salvo" sozinho não responde à pergunta que a pessoa realmente faz depois de
 * uma edição longa — *a minha última alteração entrou?*. A hora responde.
 */
export function horaDeAgora(): string {
  return new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

/**
 * Traduz o caminho técnico de um campo para onde a pessoa deve olhar.
 *
 * O servidor identifica o campo pela estrutura do pedido —
 * `refeicoes[0].itens[1].quantidade` — que é exata e ilegível. Num plano com
 * seis refeições, o que resolve é "Almoço, 2º alimento", porque é assim que a
 * refeição está rotulada na tela.
 *
 * @param nomeDaRefeicao devolve o nome da refeição naquela posição, ou nada
 *                       quando a posição não existe mais no que está na tela.
 */
export function ondeOlhar(
  caminho: string,
  nomeDaRefeicao: (indice: number) => string | undefined,
): string | null {
  const emItem = /^refeicoes\[(\d+)\]\.itens\[(\d+)\]/.exec(caminho);
  if (emItem) {
    const refeicao = nomeDaRefeicao(Number(emItem[1])) ?? `${Number(emItem[1]) + 1}ª refeição`;
    return `${refeicao}, ${Number(emItem[2]) + 1}º alimento`;
  }
  const emRefeicao = /^refeicoes\[(\d+)\]/.exec(caminho);
  if (emRefeicao) {
    return nomeDaRefeicao(Number(emRefeicao[1])) ?? `${Number(emRefeicao[1]) + 1}ª refeição`;
  }
  const ROTULOS: Record<string, string> = {
    titulo: "Título do plano",
    metodo: "Método",
    metaEnergiaKcal: "Meta de energia",
    vigenciaInicio: "Início da vigência",
    vigenciaFim: "Fim da vigência",
    orientacoes: "Orientações gerais",
    observacoesInternas: "Observações internas",
  };
  return ROTULOS[caminho] ?? null;
}
