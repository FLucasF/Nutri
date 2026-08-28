import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { explicarErro, horaDeAgora } from "../api/erros";

type Tipo = "ok" | "erro";

type Recado = { tipo: Tipo; texto: string; hora: string; chave: number };

type Painel = {
  /** Confirma que deu certo. A hora entra sozinha. */
  confirmar: (texto: string) => void;
  /** Explica o que impediu, a partir da falha e do que se tentava fazer. */
  avisar: (erro: unknown, acao: string) => void;
  limpar: () => void;
};

const Contexto = createContext<Painel | null>(null);

/** Quanto tempo a confirmação fica na tela antes de sair sozinha. */
const DURACAO_DA_CONFIRMACAO = 6000;

export function ProvedorDeRecado({ children }: { children: ReactNode }) {
  const [recado, setRecado] = useState<Recado | null>(null);
  const relogio = useRef<number | undefined>(undefined);

  const agendarSaida = useCallback((tipo: Tipo) => {
    window.clearTimeout(relogio.current);
    // Só a confirmação sai sozinha. Erro que some antes de ser lido é erro que
    // não foi comunicado — e a pessoa fica sem saber o que corrigir.
    if (tipo === "ok") {
      relogio.current = window.setTimeout(() => setRecado(null), DURACAO_DA_CONFIRMACAO);
    }
  }, []);

  const painel = useMemo<Painel>(
    () => ({
      confirmar: (texto) => {
        setRecado({ tipo: "ok", texto, hora: horaDeAgora(), chave: Date.now() });
        agendarSaida("ok");
      },
      avisar: (erro, acao) => {
        setRecado({
          tipo: "erro",
          texto: explicarErro(erro, acao),
          hora: horaDeAgora(),
          chave: Date.now(),
        });
        agendarSaida("erro");
      },
      limpar: () => {
        window.clearTimeout(relogio.current);
        setRecado(null);
      },
    }),
    [agendarSaida],
  );

  useEffect(() => () => window.clearTimeout(relogio.current), []);

  return (
    <Contexto.Provider value={painel}>
      {children}
      <TiraDeRecado recado={recado} aoFechar={painel.limpar} />
    </Contexto.Provider>
  );
}

export function useRecado(): Painel {
  const painel = useContext(Contexto);
  if (!painel) {
    throw new Error("useRecado precisa estar dentro de ProvedorDeRecado");
  }
  return painel;
}

/**
 * A tira de recado, ancorada ao pé da tela.
 *
 * A hora à esquerda e o texto à direita repetem a régua do dia: é o mesmo
 * gesto de pendurar a informação numa hora real, e é ela que responde à
 * pergunta que a pessoa faz de verdade depois de uma edição longa — *a minha
 * última alteração entrou?*.
 *
 * `role` muda com o tipo porque leitor de tela trata os dois diferente:
 * confirmação espera a frase em curso terminar, erro interrompe.
 */
function TiraDeRecado({ recado, aoFechar }: { recado: Recado | null; aoFechar: () => void }) {
  return (
    <div className="ancora-do-recado">
      {/* A região existe sempre, mesmo vazia: leitor de tela só anuncia
          mudanças em região que já estava no documento. */}
      <div aria-live="polite" aria-atomic="true" className="regiao-de-recado">
        {recado?.tipo === "ok" && (
          <div className="tira-de-recado ok" key={recado.chave}>
            <time className="recado-hora">{recado.hora}</time>
            <p className="recado-texto">{recado.texto}</p>
          </div>
        )}
      </div>
      <div aria-live="assertive" aria-atomic="true" className="regiao-de-recado">
        {recado?.tipo === "erro" && (
          <div className="tira-de-recado erro" role="alert" key={recado.chave}>
            <time className="recado-hora">{recado.hora}</time>
            <p className="recado-texto">{recado.texto}</p>
            <button type="button" className="recado-fechar" onClick={aoFechar} aria-label="Fechar aviso">
              ×
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
