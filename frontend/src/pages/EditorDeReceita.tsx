import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { formatarNutriente, MACROS_PRINCIPAIS, rotuloDe } from "../api/nutrientes";
import type { AlimentoResumo, IngredienteReceita, Medida, Receita } from "../api/types";
import BuscaDeAlimento from "../componentes/BuscaDeAlimento";
import { contar } from "../texto";

/** Ingrediente enquanto está sendo montado na tela, antes de ir ao servidor. */
type Rascunho = {
  alimentoId: number;
  descricao: string;
  medidaId?: number;
  quantidade: string;
  medidas: Medida[];
};

/**
 * Editor de receita.
 *
 * A tela existe porque a base tem 23.945 alimentos e nenhuma preparação: dá
 * para prescrever arroz e brócolis separados, não "arroz com brócolis". A
 * receita resolve isso virando um alimento como outro qualquer — depois de
 * salva, ela aparece na busca e entra num plano sem tratamento especial.
 */
export default function EditorDeReceita() {
  const { id } = useParams();
  const navegar = useNavigate();
  const editando = id !== undefined;

  const [nome, setNome] = useState("");
  const [grupo, setGrupo] = useState("");
  const [rendimento, setRendimento] = useState("");
  const [porcoes, setPorcoes] = useState("");
  const [modoPreparo, setModoPreparo] = useState("");
  const [ingredientes, setIngredientes] = useState<Rascunho[]>([]);
  const [buscaIngrediente, setBuscaIngrediente] = useState("");

  const [calculada, setCalculada] = useState<Receita | null>(null);
  const [carregando, setCarregando] = useState(editando);
  const [salvando, setSalvando] = useState(false);
  const campos = useErrosDeCampo();
  const recado = useRecado();
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    if (!editando) return;
    setCarregando(true);
    try {
      const r = await api.receitas.detalhar(Number(id));
      setCalculada(r);
      setNome(r.nome);
      setGrupo(r.grupo ?? "");
      setRendimento(r.rendimentoEstimado ? "" : String(r.rendimentoGramas));
      setPorcoes(r.porcoes ? String(r.porcoes) : "");
      setModoPreparo(r.modoPreparo ?? "");
      setIngredientes(await Promise.all(r.ingredientes.map(paraRascunho)));
    } catch (e) {
      setErro(explicarErro(e, "abrir a receita"));
    } finally {
      setCarregando(false);
    }
  }, [id, editando]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function paraRascunho(i: IngredienteReceita): Promise<Rascunho> {
    const detalhe = await api.alimentos.detalhar(i.alimentoId).catch(() => null);
    // A quantidade chega formatada ("2 colheres de sopa"); para editar é
    // preciso o número, que é o primeiro termo.
    const numero = i.quantidade.split(" ")[0]?.replace(",", ".") ?? String(i.gramas);
    return {
      alimentoId: i.alimentoId,
      descricao: i.descricao,
      medidaId: i.medidaId,
      quantidade: numero,
      medidas: detalhe?.medidas ?? [],
    };
  }

  async function adicionar(resumo: AlimentoResumo) {
    const detalhe = await api.alimentos.detalhar(resumo.id).catch(() => null);
    const padrao = detalhe?.medidas.find((m) => m.padrao) ?? detalhe?.medidas[0];
    setIngredientes((atual) => [
      ...atual,
      {
        alimentoId: resumo.id,
        descricao: resumo.descricao,
        medidaId: padrao?.id,
        quantidade: padrao ? "1" : "100",
        medidas: detalhe?.medidas ?? [],
      },
    ]);
  }

  function alterar(indice: number, mudanca: Partial<Rascunho>) {
    setIngredientes((atual) =>
      atual.map((i, n) => (n === indice ? { ...i, ...mudanca } : i)),
    );
  }

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setSalvando(true);
    try {
      const corpo = {
        nome: nome.trim(),
        grupo: grupo.trim() || undefined,
        rendimentoGramas: rendimento ? Number(rendimento.replace(",", ".")) : undefined,
        porcoes: porcoes ? Number(porcoes) : undefined,
        modoPreparo: modoPreparo.trim() || undefined,
        ingredientes: ingredientes.map((i) => ({
          alimentoId: i.alimentoId,
          medidaId: i.medidaId,
          quantidade: Number(i.quantidade.replace(",", ".")) || 0,
        })),
      };
      const salva = editando
        ? await api.receitas.atualizar(Number(id), corpo)
        : await api.receitas.criar(corpo);
      setCalculada(salva);
      recado.confirmar(`"${salva.nome}" salva.`);
      if (!editando) {
        navegar(`/receitas/${salva.id}`, { replace: true });
      }
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "salvar a receita"));
      }
    } finally {
      setSalvando(false);
    }
  }

  if (carregando) {
    return <p className="carregando">Carregando…</p>;
  }

  const pesoDosIngredientes = ingredientes.reduce((soma, i) => soma + estimarGramas(i), 0);

  return (
    <form onSubmit={salvar}>
      <div className="cabecalho-pagina">
        <div>
          <h1>{editando ? nome || "Receita" : "Nova receita"}</h1>
          <p>
            {contar(ingredientes.length, "ingrediente", "ingredientes")}
            {calculada ? ` · rende ${formatarGramas(calculada.rendimentoGramas)}` : ""}
          </p>
        </div>
        <div className="linha">
          <Link className="botao secundario" to="/alimentos?fonte=RECEITA">
            Voltar
          </Link>
          <button className="botao" type="submit" disabled={salvando}>
            {salvando ? "Salvando…" : "Salvar receita"}
          </button>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      <div className="editor-plano">
        <div>
          <div className="cartao" style={{ marginBottom: "0.9rem" }}>
            <div className="grade duas">
              <div className="campo">
                <label htmlFor="rc-nome">Nome da receita</label>
                <input
                  id="rc-nome"
                  name="nome"
                  value={nome}
                  onChange={(e) => setNome(e.target.value)}
                  required
                  placeholder="Panqueca de aveia"
                  {...campos.props("nome")}
                />
                <ErroDeCampo campo="nome" erros={campos.erros} />
              </div>
              <div className="campo">
                <label htmlFor="rc-grupo">Grupo</label>
                <input
                  id="rc-grupo"
                  name="grupo"
                  value={grupo}
                  onChange={(e) => setGrupo(e.target.value)}
                  placeholder="Lanches"
                  {...campos.props("grupo")}
                />
                <ErroDeCampo campo="grupo" erros={campos.erros} />
              </div>
              <div className="campo">
                <label htmlFor="rc-rendimento">Peso pronto (g)</label>
                <input
                  id="rc-rendimento"
                  name="rendimentoGramas"
                  inputMode="decimal"
                  value={rendimento}
                  onChange={(e) => setRendimento(e.target.value)}
                  placeholder={pesoDosIngredientes ? String(Math.round(pesoDosIngredientes)) : ""}
                  {...campos.props("rendimentoGramas")}
                />
                <ErroDeCampo campo="rendimentoGramas" erros={campos.erros} />
                <span className="minusculo">
                  Pese a preparação pronta. Cozinhar perde ou ganha água, e sem este número a
                  composição por 100 g é estimativa.
                </span>
              </div>
              <div className="campo">
                <label htmlFor="rc-porcoes">Rende quantas porções</label>
                <input
                  id="rc-porcoes"
                  name="porcoes"
                  inputMode="numeric"
                  value={porcoes}
                  onChange={(e) => setPorcoes(e.target.value)}
                  placeholder="8"
                  {...campos.props("porcoes")}
                />
                <ErroDeCampo campo="porcoes" erros={campos.erros} />
                <span className="minusculo">
                  Gera a medida “1 porção”, que é como a receita chega ao paciente.
                </span>
              </div>
            </div>
          </div>

          <div className="refeicao">
            <div className="refeicao-topo">
              <strong style={{ flex: 1 }}>Ingredientes</strong>
              <span className="etiqueta">
                cru: {formatarGramas(pesoDosIngredientes)}
              </span>
            </div>

            {ingredientes.length === 0 ? (
              <p className="vazio" style={{ padding: "1rem" }}>
                Busque um alimento abaixo para montar a receita.
              </p>
            ) : (
              ingredientes.map((i, n) => (
                <div className="item-linha" key={`${i.alimentoId}-${n}`}>
                  <div className="descricao-item">
                    {i.descricao}
                    <small>{formatarGramas(estimarGramas(i))}</small>
                  </div>
                  <input
                    inputMode="decimal"
                    value={i.quantidade}
                    onChange={(e) => alterar(n, { quantidade: e.target.value })}
                    aria-label={`Quantidade de ${i.descricao}`}
                  />
                  <select
                    value={i.medidaId ?? ""}
                    onChange={(e) =>
                      alterar(n, { medidaId: e.target.value ? Number(e.target.value) : undefined })
                    }
                    aria-label={`Medida de ${i.descricao}`}
                  >
                    <option value="">gramas</option>
                    {i.medidas.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.descricao}
                      </option>
                    ))}
                  </select>
                  <span />
                  <button
                    type="button"
                    className="botao perigo pequeno"
                    onClick={() => setIngredientes((a) => a.filter((_, x) => x !== n))}
                  >
                    Remover
                  </button>
                </div>
              ))
            )}

            <div style={{ padding: "0.7rem 0.9rem" }}>
              <BuscaDeAlimento
                valorLivre={buscaIngrediente}
                aoDigitarLivre={setBuscaIngrediente}
                aoEscolher={(resumo) => {
                  void adicionar(resumo);
                  // Limpa para o próximo: montar receita é adicionar vários
                  // seguidos, e reaproveitar o termo anterior atrapalharia.
                  setBuscaIngrediente("");
                }}
                desabilitado={false}
              />
            </div>
          </div>

          <div className="cartao" style={{ marginTop: "0.9rem" }}>
            <div className="campo">
              <label htmlFor="rc-preparo">Modo de preparo</label>
              <textarea
                id="rc-preparo"
                name="modoPreparo"
                rows={6}
                value={modoPreparo}
                onChange={(e) => setModoPreparo(e.target.value)}
                placeholder="Bata os ingredientes, despeje na frigideira quente…"
                {...campos.props("modoPreparo")}
              />
              <ErroDeCampo campo="modoPreparo" erros={campos.erros} />
            </div>
          </div>
        </div>

        <aside className="painel-totais">
          {calculada ? (
            <ResumoDaReceita receita={calculada} />
          ) : (
            <div className="cartao">
              <h2>Composição</h2>
              <p className="discreto" style={{ marginTop: "0.4rem", marginBottom: 0 }}>
                Salve a receita para ver a composição calculada.
              </p>
            </div>
          )}
        </aside>
      </div>
    </form>
  );
}

function ResumoDaReceita({ receita }: { receita: Receita }) {
  const incompletos = new Set(receita.nutrientesIncompletos);
  // A nota só faz sentido se algum dos nutrientes exibidos estiver em itálico.
  // A receita pode ter dezenas de incompletos que não aparecem nesta lista, e
  // avisar sobre itálico onde não há itálico manda o leitor procurar nada.
  const algumExibidoIncompleto = MACROS_PRINCIPAIS.some((c) => incompletos.has(c));

  return (
    <>
      <div className="cartao">
        <h2>Rendimento</h2>
        <div className="nutriente-linha">
          <span>Ingredientes</span>
          <span>{formatarGramas(receita.pesoDosIngredientes)}</span>
        </div>
        <div className="nutriente-linha">
          <span>Preparação pronta</span>
          <span>{formatarGramas(receita.rendimentoGramas)}</span>
        </div>
        {receita.gramasPorPorcao !== undefined && (
          <div className="nutriente-linha">
            <span>Cada porção</span>
            <span>{formatarGramas(receita.gramasPorPorcao)}</span>
          </div>
        )}
        {receita.rendimentoEstimado && (
          <div className="aviso atencao" style={{ marginTop: "0.7rem" }}>
            O peso da preparação pronta não foi informado, então a soma dos ingredientes foi
            usada. Cozinhar muda o peso: pese e informe para que a composição seja exata.
          </div>
        )}
      </div>

      <div className="cartao">
        <h2>Por 100 g</h2>
        {MACROS_PRINCIPAIS.map((chave) => (
          <div
            className={`nutriente-linha ${incompletos.has(chave) ? "ausente" : ""}`}
            key={chave}
          >
            <span>{rotuloDe(chave)}</span>
            <span>
              {formatarNutriente(
                receita.composicaoPor100g[chave as keyof typeof receita.composicaoPor100g] as
                  | number
                  | undefined,
                unidadeDe(chave),
              )}
            </span>
          </div>
        ))}
        {algumExibidoIncompleto && (
          <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
            Em itálico: somado a partir de apenas parte dos ingredientes. O valor é um mínimo,
            não um total — algum ingrediente não tem esse nutriente determinado na fonte.
          </p>
        )}
      </div>

      {!algumExibidoIncompleto && incompletos.size > 0 && (
        <div className="cartao">
          <p className="minusculo" style={{ margin: 0 }}>
            {contar(incompletos.size, "nutriente foi somado", "nutrientes foram somados")} a partir
            de apenas parte dos ingredientes, e são mínimos e não totais. Nenhum deles está entre
            os exibidos acima.
          </p>
        </div>
      )}

      {receita.composicaoDaPorcao && (
        <div className="cartao">
          <h2>Por porção</h2>
          {MACROS_PRINCIPAIS.map((chave) => (
            <div className="nutriente-linha" key={chave}>
              <span>{rotuloDe(chave)}</span>
              <span>
                {formatarNutriente(
                  receita.composicaoDaPorcao![
                    chave as keyof typeof receita.composicaoDaPorcao
                  ] as number | undefined,
                  unidadeDe(chave),
                )}
              </span>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

/** Peso aproximado do ingrediente enquanto a receita não foi salva. */
function estimarGramas(i: Rascunho): number {
  const quantidade = Number(i.quantidade.replace(",", ".")) || 0;
  const medida = i.medidas.find((m) => m.id === i.medidaId);
  return medida ? quantidade * medida.gramas : quantidade;
}

function formatarGramas(valor?: number): string {
  if (valor === undefined || valor === null) return "—";
  return `${Number(valor.toFixed(1)).toLocaleString("pt-BR")} g`;
}

const UNIDADES: Record<string, string> = {
  energiaKcal: "kcal",
  proteinaG: "g",
  carboidratoG: "g",
  lipideosG: "g",
  fibraG: "g",
  sodioMg: "mg",
};

function unidadeDe(chave: string): string {
  return UNIDADES[chave] ?? "";
}
