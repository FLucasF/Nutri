import { useCallback, useEffect, useState } from "react";
import { ErroApi, api } from "../api/client";
import { explicarErro } from "../api/erros";
import { useRecado } from "../componentes/Recado";
import type { Questionario } from "../api/types";
import { contar } from "../texto";

/**
 * Biblioteca de questionários.
 *
 * O sistema traz um modelo genérico de pré-consulta, de autoria própria.
 * Instrumentos publicados — rastreamento metabólico, FINDRISC, escalas de sono
 * — têm licença própria, e cabe a cada consultório cadastrar os que tem direito
 * de usar. É a mesma razão pela qual a TBCA não está no acervo de alimentos.
 */
export default function Questionarios() {
  const recado = useRecado();
  const [questionarios, setQuestionarios] = useState<Questionario[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [aberto, setAberto] = useState<number | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      setQuestionarios(await api.questionarios.listar());
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Falha ao carregar os questionários.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function duplicar(q: Questionario) {
    try {
      const copia = await api.questionarios.duplicar(q.id);
      recado.confirmar("Cópia criada na sua biblioteca, pronta para editar.");
      await carregar();
      setAberto(copia.id);
    } catch (e) {
      setErro(explicarErro(e, "duplicar"));
    }
  }

  async function remover(q: Questionario) {
    if (!confirm(`Remover "${q.nome}" da biblioteca?`)) return;
    try {
      await api.questionarios.remover(q.id);
      recado.confirmar(`"${q.nome}" saiu da biblioteca. As respostas já recebidas continuam.`);
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "remover o questionário"));
    }
  }

  const meus = questionarios.filter((q) => !q.modeloDoSistema);
  const modelos = questionarios.filter((q) => q.modeloDoSistema);

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Questionários</h1>
          <p>
            {contar(meus.length, "formulário seu", "formulários seus")} ·{" "}
            {contar(modelos.length, "modelo do sistema", "modelos do sistema")}
          </p>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : (
        <div className="lista-orientacoes">
          {[...meus, ...modelos].map((q) => (
            <article
              className={`cartao orientacao ${q.modeloDoSistema ? "modelo" : ""}`}
              key={q.id}
            >
              <header>
                <button
                  type="button"
                  className="titulo-orientacao"
                  onClick={() => setAberto(aberto === q.id ? null : q.id)}
                >
                  <h2>{q.nome}</h2>
                </button>
                <div className="linha" style={{ gap: "0.3rem" }}>
                  {q.pontuavel && <span className="etiqueta ambar">pontuável</span>}
                  {q.modeloDoSistema && <span className="etiqueta">do sistema</span>}
                </div>
              </header>

              <p className="corpo-orientacao aberta">
                {q.descricao ?? `${contar(q.perguntas.length, "pergunta", "perguntas")}.`}
              </p>

              {aberto === q.id && (
                <ol style={{ margin: "0.7rem 0 0", paddingLeft: "1.2rem" }}>
                  {q.perguntas.map((p) => (
                    <li key={p.id} style={{ marginBottom: "0.4rem", fontSize: "0.9rem" }}>
                      {p.enunciado}
                      {p.obrigatoria && <span className="minusculo"> (obrigatória)</span>}
                      {p.opcoes.length > 0 && (
                        <div className="minusculo">
                          {p.opcoes
                            .map((o) => (o.pontos !== undefined ? `${o.rotulo} = ${o.pontos}` : o.rotulo))
                            .join(" · ")}
                        </div>
                      )}
                    </li>
                  ))}
                </ol>
              )}

              <div className="linha" style={{ marginTop: "0.6rem" }}>
                <button
                  type="button"
                  className="botao secundario pequeno"
                  onClick={() => setAberto(aberto === q.id ? null : q.id)}
                >
                  {aberto === q.id ? "Recolher" : "Ver perguntas"}
                </button>
                <button
                  type="button"
                  className="botao secundario pequeno"
                  onClick={() => duplicar(q)}
                >
                  Duplicar
                </button>
                {q.editavel && (
                  <button
                    type="button"
                    className="botao perigo pequeno"
                    onClick={() => remover(q)}
                  >
                    Remover
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      <div className="cartao" style={{ marginTop: "0.9rem" }}>
        <h2>Sobre instrumentos publicados</h2>
        <p className="discreto" style={{ marginTop: "0.4rem", marginBottom: 0 }}>
          O sistema traz um modelo genérico de pré-consulta, de autoria própria. Instrumentos
          publicados — rastreamento metabólico, FINDRISC, escalas de sono — têm licença própria,
          e cabe a você cadastrar os que tem direito de usar. É a mesma razão pela qual a TBCA
          não está no acervo de alimentos.
        </p>
      </div>
    </>
  );
}
