import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { ErroApi, api } from "../api/client";
import { explicarErro } from "../api/erros";
import type { FormularioPublico } from "../api/types";

/**
 * O questionário como o paciente o responde.
 *
 * Sem login: quem tem o link, responde — o mesmo mecanismo pelo qual ele lê o
 * plano. A página é deliberadamente mais simples que o resto do sistema porque
 * quem a abre não é usuário do produto: abre uma vez, responde e sai.
 */
export default function FormularioDoPaciente() {
  const { identificador } = useParams();

  const [formulario, setFormulario] = useState<FormularioPublico | null>(null);
  const [respostas, setRespostas] = useState<Record<number, string>>({});
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [pronto, setPronto] = useState(false);

  useEffect(() => {
    if (!identificador) return;
    api.questionarios
      .formulario(identificador)
      .then((f) => {
        setFormulario(f);
        setPronto(f.jaRespondido);
      })
      .catch((e) =>
        setErro(
          e instanceof ErroApi
            ? e.message
            : "Não foi possível abrir o formulário. Verifique sua conexão.",
        ),
      )
      .finally(() => setCarregando(false));
  }, [identificador]);

  useEffect(() => {
    if (formulario?.titulo) {
      document.title = `${formulario.titulo} — NutriPlan`;
    }
  }, [formulario]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!identificador) return;
    setErro(null);
    setEnviando(true);
    try {
      await api.questionarios.responder(
        identificador,
        Object.entries(respostas)
          .filter(([, valor]) => valor.trim())
          .map(([perguntaId, valor]) => ({ perguntaId: Number(perguntaId), valor })),
      );
      setPronto(true);
    } catch (e) {
      setErro(explicarErro(e, "enviar as respostas"));
    } finally {
      setEnviando(false);
    }
  }

  if (carregando) {
    return (
      <div className="pagina-paciente">
        <p className="carregando">Carregando…</p>
      </div>
    );
  }

  if (erro && !formulario) {
    return (
      <div className="pagina-paciente">
        <div className="plano-paciente" style={{ paddingTop: "3rem" }}>
          <div className="cartao" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h1 style={{ fontSize: "1.5rem" }}>Formulário não encontrado</h1>
            <p className="discreto" style={{ marginTop: "0.6rem" }}>{erro}</p>
          </div>
        </div>
      </div>
    );
  }

  if (!formulario) return null;

  return (
    <div className="pagina-paciente">
      <div className="plano-paciente">
        <header className="capa-plano">
          {formulario.consultorio && <span className="eyebrow">{formulario.consultorio}</span>}
          <h1>{formulario.titulo}</h1>
          {formulario.descricao && <p>{formulario.descricao}</p>}
        </header>

        {pronto ? (
          <div className="cartao" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h2>Respostas enviadas</h2>
            <p className="discreto" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
              Obrigado. Seu nutricionista já recebeu. Pode fechar esta página.
            </p>
          </div>
        ) : (
          <form className="cartao" onSubmit={enviar}>
            {erro && (
              <div className="aviso erro" role="alert" style={{ marginBottom: "1rem" }}>
                {erro}
              </div>
            )}

            {formulario.perguntas.map((p) => (
              <div className="campo" key={p.id} style={{ marginBottom: "1.2rem" }}>
                <label htmlFor={`p-${p.id}`}>
                  {p.enunciado}
                  {p.obrigatoria && <span aria-hidden> *</span>}
                </label>

                {p.tipo === "ESCOLHA_UNICA" || p.tipo === "MULTIPLA" ? (
                  <select
                    id={`p-${p.id}`}
                    value={respostas[p.id] ?? ""}
                    onChange={(e) =>
                      setRespostas((r) => ({ ...r, [p.id]: e.target.value }))
                    }
                    required={p.obrigatoria}
                  >
                    <option value="">Selecione…</option>
                    {p.opcoes.map((o) => (
                      <option key={o.rotulo} value={o.rotulo}>
                        {o.rotulo}
                      </option>
                    ))}
                  </select>
                ) : p.tipo === "NUMERO" ? (
                  <input
                    id={`p-${p.id}`}
                    inputMode="decimal"
                    value={respostas[p.id] ?? ""}
                    onChange={(e) => setRespostas((r) => ({ ...r, [p.id]: e.target.value }))}
                    required={p.obrigatoria}
                  />
                ) : (
                  <textarea
                    id={`p-${p.id}`}
                    rows={3}
                    value={respostas[p.id] ?? ""}
                    onChange={(e) => setRespostas((r) => ({ ...r, [p.id]: e.target.value }))}
                    required={p.obrigatoria}
                  />
                )}

                {p.ajuda && <span className="minusculo">{p.ajuda}</span>}
              </div>
            ))}

            <button className="botao" type="submit" disabled={enviando} style={{ width: "100%" }}>
              {enviando ? "Enviando…" : "Enviar respostas"}
            </button>
            <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
              As respostas vão direto para o seu nutricionista. O formulário aceita um envio só.
            </p>
          </form>
        )}
      </div>
    </div>
  );
}
