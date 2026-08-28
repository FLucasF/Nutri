import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { errosPorCampo } from "../api/erros";

/**
 * Erros de validação grudados no campo que os causou.
 *
 * O servidor sempre mandou o erro por campo; a tela é que juntava tudo com
 * ". " numa frase só e a mostrava no topo do formulário. Num formulário de
 * vinte campos isso vira uma caça: a pessoa lê "O peso deve ser maior que
 * zero" e precisa descobrir sozinha onde fica o peso.
 *
 * Aqui cada mensagem fica embaixo do seu campo, o campo é marcado como
 * inválido para leitor de tela, e o foco vai para o primeiro deles — que é o
 * que a WCAG pede em 3.3.1 (identificar o erro) e 3.3.3 (sugerir a correção).
 */
export function useErrosDeCampo() {
  const [erros, setErros] = useState<Record<string, string>>({});

  /** O campo que deve receber o foco assim que a tela mostrar o erro. */
  const aFocar = useRef<string | null>(null);

  /**
   * Lê a falha e distribui as mensagens pelos campos.
   *
   * @returns true quando havia erro por campo — o chamador usa isso para não
   *          mostrar também a tira geral, que repetiria a mesma informação.
   */
  const aplicar = useCallback((e: unknown) => {
    const mapa = errosPorCampo(e);
    setErros(mapa);
    aFocar.current = Object.keys(mapa)[0] ?? null;
    return Object.keys(mapa).length > 0;
  }, []);

  /**
   * O foco só pode ir para o campo depois que a marcação de inválido existe
   * no documento.
   *
   * Tentar no mesmo passo do `aplicar` não funciona: ali o React ainda não
   * renderizou, e o leitor de tela leria o campo sem a mensagem que o
   * `aria-describedby` acabou de apontar. O efeito roda depois da pintura, que
   * é quando as duas coisas já estão no lugar.
   */
  useEffect(() => {
    const campo = aFocar.current;
    if (!campo) return;
    aFocar.current = null;
    const alvo = document.querySelector<HTMLElement>(
      `[name="${CSS.escape(campo)}"], #${CSS.escape(campo)}`,
    );
    if (!alvo) return;
    alvo.focus({ preventScroll: true });
    alvo.scrollIntoView({ block: "center", behavior: "smooth" });
  }, [erros]);

  const limpar = useCallback(() => setErros({}), []);

  /** Some com o aviso assim que a pessoa mexe no campo. */
  const limparCampo = useCallback((campo: string) => {
    setErros((atual) => {
      if (!atual[campo]) return atual;
      const proximo = { ...atual };
      delete proximo[campo];
      return proximo;
    });
  }, []);

  const props = useCallback(
    (campo: string) => ({
      "aria-invalid": erros[campo] ? (true as const) : undefined,
      "aria-describedby": erros[campo] ? `erro-${campo}` : undefined,
      onInput: () => limparCampo(campo),
    }),
    [erros, limparCampo],
  );

  return useMemo(
    () => ({ erros, aplicar, limpar, limparCampo, props, de: (c: string) => erros[c] }),
    [erros, aplicar, limpar, limparCampo, props],
  );
}

/** A mensagem embaixo do campo. Nada é desenhado quando o campo está certo. */
export function ErroDeCampo({ campo, erros }: { campo: string; erros: Record<string, string> }) {
  const mensagem = erros[campo];
  if (!mensagem) return null;
  return (
    <span className="mensagem-de-campo" id={`erro-${campo}`}>
      {mensagem}
    </span>
  );
}
