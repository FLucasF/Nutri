import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ErroApi, api } from "../api/client";
import type { PlanoResumo } from "../api/types";
import { EtiquetaStatus } from "./PacienteDetalhe";

type Aba = "planos" | "modelos";

export default function Prescricoes() {
  const navegar = useNavigate();

  const [aba, setAba] = useState<Aba>("planos");
  const [planos, setPlanos] = useState<PlanoResumo[]>([]);
  const [termo, setTermo] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const pagina = await api.prescricoes.listar({
        modelo: aba === "modelos",
        termo: termo.trim() || undefined,
        size: 50,
      });
      setPlanos(pagina.content);
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Falha ao carregar as prescrições.");
    } finally {
      setCarregando(false);
    }
  }, [aba, termo]);

  useEffect(() => {
    const relogio = setTimeout(carregar, termo ? 300 : 0);
    return () => clearTimeout(relogio);
  }, [carregar, termo]);

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Prescrições</h1>
          <p>Planos alimentares e modelos reaproveitáveis.</p>
        </div>
        <button className="botao" onClick={() => navegar("/prescricoes/novo")}>
          Novo plano
        </button>
      </div>

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="linha" style={{ gap: "0.3rem" }}>
            <button
              className={`botao ${aba === "planos" ? "" : "secundario"} pequeno`}
              onClick={() => setAba("planos")}
            >
              Planos
            </button>
            <button
              className={`botao ${aba === "modelos" ? "" : "secundario"} pequeno`}
              onClick={() => setAba("modelos")}
            >
              Modelos
            </button>
          </div>
          <div className="campo" style={{ flex: 1, minWidth: 200 }}>
            <input
              value={termo}
              onChange={(e) => setTermo(e.target.value)}
              placeholder="Buscar pelo título do plano"
              aria-label="Buscar plano"
            />
          </div>
        </div>
      </div>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : planos.length === 0 ? (
        <div className="cartao vazio">
          {aba === "modelos"
            ? "Nenhum modelo salvo. Marque um plano como modelo para reaproveitá-lo em outro paciente."
            : "Nenhuma prescrição ainda. Use Nova prescrição para montar o primeiro plano."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Plano</th>
                {aba === "planos" && <th>Paciente</th>}
                <th>Método</th>
                <th>Situação</th>
                <th className="num">Itens</th>
                <th className="num">kcal/dia</th>
              </tr>
            </thead>
            <tbody>
              {planos.map((plano) => (
                <tr
                  key={plano.id}
                  className="clicavel"
                  onClick={() => navegar(`/prescricoes/${plano.id}`)}
                >
                  <td>
                    <strong>{plano.titulo}</strong>
                    <div className="minusculo">
                      {plano.refeicoes} {plano.refeicoes === 1 ? "refeição" : "refeições"}
                    </div>
                  </td>
                  {aba === "planos" && <td className="discreto">{plano.pacienteNome ?? "—"}</td>}
                  <td className="discreto">{rotuloMetodo(plano.metodo)}</td>
                  <td>
                    {plano.modelo ? (
                      <span className="etiqueta">modelo</span>
                    ) : (
                      <EtiquetaStatus status={plano.status} />
                    )}
                  </td>
                  <td className="num">{plano.itens}</td>
                  <td className="num">{plano.energiaKcal ? Math.round(plano.energiaKcal) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

export function rotuloMetodo(metodo: string) {
  if (metodo === "ALIMENTOS") return "por alimentos";
  if (metodo === "EQUIVALENTES") return "por equivalentes";
  return "qualitativo";
}
