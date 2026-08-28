import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { useRecado } from "../componentes/Recado";
import { NUTRIENTES, formatarNutriente } from "../api/nutrientes";
import type { AlimentoDetalhe as Alimento, PorcaoCalculada } from "../api/types";

const GRUPOS_EXIBIDOS = [
  { grupo: "energia", titulo: "Energia" },
  { grupo: "macro", titulo: "Macronutrientes" },
  { grupo: "lipidio", titulo: "Lipídios" },
  { grupo: "mineral", titulo: "Minerais" },
  { grupo: "vitamina", titulo: "Vitaminas" },
  { grupo: "outro", titulo: "Outros" },
] as const;

export default function AlimentoDetalhe() {
  const { id } = useParams();
  const alimentoId = Number(id);

  const [alimento, setAlimento] = useState<Alimento | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const [quantidade, setQuantidade] = useState("1");
  const [medidaId, setMedidaId] = useState<string>("");
  const [porcao, setPorcao] = useState<PorcaoCalculada | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    try {
      const dados = await api.alimentos.detalhar(alimentoId);
      setAlimento(dados);
      const padrao = dados.medidas.find((m) => m.padrao) ?? dados.medidas[0];
      setMedidaId(padrao ? String(padrao.id) : "");
    } catch (e) {
      setErro(explicarErro(e, "abrir o alimento"));
    } finally {
      setCarregando(false);
    }
  }, [alimentoId]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  // Recalcula a porção sempre que quantidade ou medida mudam.
  useEffect(() => {
    const valor = Number(quantidade.replace(",", "."));
    if (!alimento || !Number.isFinite(valor) || valor <= 0) {
      setPorcao(null);
      return;
    }
    let cancelado = false;
    api.alimentos
      .porcao(alimento.id, valor, medidaId ? Number(medidaId) : undefined)
      .then((resultado) => {
        if (!cancelado) setPorcao(resultado);
      })
      .catch(() => {
        if (!cancelado) setPorcao(null);
      });
    return () => {
      cancelado = true;
    };
  }, [alimento, quantidade, medidaId]);

  if (carregando) return <p className="carregando">Carregando…</p>;
  if (erro && !alimento) return <div className="aviso erro">{erro}</div>;
  if (!alimento) return null;

  const exibida = porcao?.composicao ?? alimento.composicao;
  const base = porcao ? porcao.medidaUsada : "100 g";

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <Link to="/alimentos" className="minusculo">
            ← Alimentos
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>{alimento.descricao}</h1>
          <p>
            {alimento.grupo ?? "Sem grupo"}
            {alimento.marca ? ` · ${alimento.marca}` : ""} · Fonte: {alimento.fonteDescricao}
          </p>
        </div>
        {alimento.basePublica && <span className="etiqueta">tabela de referência</span>}
      </div>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      <div className="cartao" style={{ marginBottom: "1.1rem" }}>
        <h2>Calcular porção</h2>
        <div className="linha" style={{ marginTop: "0.7rem" }}>
          <div className="campo" style={{ width: 120 }}>
            <label htmlFor="qtd">Quantidade</label>
            <input id="qtd" inputMode="decimal" value={quantidade} onChange={(e) => setQuantidade(e.target.value)} />
          </div>
          <div className="campo" style={{ flex: 1, minWidth: 200 }}>
            <label htmlFor="med">Medida</label>
            <select id="med" value={medidaId} onChange={(e) => setMedidaId(e.target.value)}>
              <option value="">gramas</option>
              {alimento.medidas.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.descricao} ({formatarPeso(m.gramas)})
                  {m.doAcervoBase ? "" : " · sua"}
                </option>
              ))}
            </select>
          </div>
          {porcao && (
            <div style={{ paddingTop: "1.1rem" }}>
              <span className="etiqueta verde">{formatarPeso(porcao.gramas)}</span>
            </div>
          )}
        </div>
      </div>

      <div className="cartao" style={{ marginBottom: "1.1rem" }}>
        <div className="linha" style={{ justifyContent: "space-between" }}>
          <h2>Composição</h2>
          <span className="minusculo">por {base}</span>
        </div>

        <div className="grade duas" style={{ marginTop: "0.8rem" }}>
          {GRUPOS_EXIBIDOS.map(({ grupo, titulo }) => {
            const doGrupo = NUTRIENTES.filter((n) => n.grupo === grupo);
            if (doGrupo.length === 0) return null;
            return (
              <div key={grupo}>
                <h3 style={{ marginBottom: "0.3rem" }}>{titulo}</h3>
                {doGrupo.map((n) => {
                  const valor = exibida[n.chave];
                  const ausente = valor === undefined || valor === null;
                  return (
                    <div key={n.chave} className={`nutriente-linha ${ausente ? "ausente" : ""}`}>
                      <span>{n.rotulo}</span>
                      <span>{formatarNutriente(valor, n.unidade)}</span>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      <Medidas alimento={alimento} aoMudar={carregar} />
    </>
  );
}

function formatarPeso(gramas: number) {
  const texto = gramas % 1 === 0 ? String(gramas) : gramas.toFixed(gramas < 1 ? 2 : 1);
  return `${texto.replace(".", ",")} g`;
}

function Medidas({ alimento, aoMudar }: { alimento: Alimento; aoMudar: () => void }) {
  const [descricao, setDescricao] = useState("");
  const [gramas, setGramas] = useState("");
  const [padrao, setPadrao] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function adicionar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await api.alimentos.adicionarMedida(alimento.id, {
        descricao: descricao.trim(),
        gramas: Number(gramas.replace(",", ".")),
        padrao,
      });
      setDescricao("");
      setGramas("");
      setPadrao(false);
      aoMudar();
    } catch (e) {
      setErro(explicarErro(e, "cadastrar a porção"));
    } finally {
      setEnviando(false);
    }
  }

  const recado = useRecado();

  async function remover(medidaId: number) {
    setErro(null);
    try {
      await api.alimentos.removerMedida(alimento.id, medidaId);
      recado.confirmar("Porção removida.");
      aoMudar();
    } catch (e) {
      setErro(explicarErro(e, "remover a porção"));
    }
  }

  return (
    <div className="cartao">
      <h2>Porções usuais</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        É o que torna o plano legível para o paciente: ninguém serve 5 g de sal, serve uma pitada.
        Você pode cadastrar sua própria versão de qualquer porção — a sua aparece primeiro, e as que
        acompanham o sistema continuam disponíveis para os demais.
      </p>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.85rem" }}>{erro}</div>}

      {alimento.medidas.length === 0 ? (
        <div className="vazio" style={{ padding: "1.2rem" }}>
          Este alimento ainda não tem porção usual. Cadastre uma para prescrevê-lo em colheres,
          fatias ou unidades — e não só em gramas.
        </div>
      ) : (
        <div className="rolagem" style={{ marginBottom: "0.9rem" }}>
          <table>
            <thead>
              <tr>
                <th>Porção</th>
                <th className="num">Peso</th>
                <th>Origem</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {alimento.medidas.map((m) => (
                <tr key={m.id}>
                  <td>
                    {m.descricao}
                    {m.padrao && <span className="etiqueta verde" style={{ marginLeft: "0.4rem" }}>padrão</span>}
                  </td>
                  <td className="num">{formatarPeso(m.gramas)}</td>
                  <td>
                    <span className="etiqueta">{m.doAcervoBase ? "do sistema" : "sua"}</span>
                  </td>
                  <td style={{ textAlign: "right" }}>
                    {m.editavel && (
                      <button className="botao perigo pequeno" onClick={() => remover(m.id)}>
                        Remover
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form onSubmit={adicionar}>
        <div className="linha">
          <div className="campo" style={{ flex: 2, minWidth: 190 }}>
            <label htmlFor="nova-med">Nova porção</label>
            <input
              id="nova-med"
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
              placeholder="colher de servir da clínica"
              required
            />
          </div>
          <div className="campo" style={{ width: 110 }}>
            <label htmlFor="nova-med-g">Peso (g)</label>
            <input
              id="nova-med-g"
              inputMode="decimal"
              value={gramas}
              onChange={(e) => setGramas(e.target.value)}
              required
            />
          </div>
          <label className="linha" style={{ gap: "0.35rem", paddingTop: "1.1rem" }}>
            <input
              type="checkbox"
              checked={padrao}
              onChange={(e) => setPadrao(e.target.checked)}
              style={{ width: "auto" }}
            />
            <span className="discreto">Padrão</span>
          </label>
          <button className="botao" type="submit" disabled={enviando} style={{ marginTop: "1.1rem" }}>
            Adicionar
          </button>
        </div>
      </form>
    </div>
  );
}
