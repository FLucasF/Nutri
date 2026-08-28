import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { useRecado } from "../componentes/Recado";
import { formatarBr, hojeIso } from "../api/datas";
import type {
  ExameResultado,
  ParametroExame,
  Paciente,
  SerieDeExame,
  SolicitacaoDeExame,
} from "../api/types";
import { contar } from "../texto";

/** Cor da etiqueta conforme a posição frente à referência. */
const CLASSE_POR_CLASSIFICACAO: Record<string, string> = {
  ABAIXO: "ambar",
  NORMAL: "verde",
  ACIMA: "vermelha",
};

/**
 * Exames laboratoriais do paciente.
 *
 * A tela mostra a faixa de referência **usada na entrada** junto de cada
 * resultado, e não a cadastrada hoje. É o que permite ler um exame de dois anos
 * atrás sabendo contra o que ele foi classificado — faixa de referência depende
 * do método do laboratório e muda com o tempo.
 */
export default function Exames() {
  const recado = useRecado();
  const { id } = useParams();
  const pacienteId = Number(id);

  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [exames, setExames] = useState<ExameResultado[]>([]);
  const [parametros, setParametros] = useState<ParametroExame[]>([]);
  const [solicitacoes, setSolicitacoes] = useState<SolicitacaoDeExame[]>([]);
  const [serie, setSerie] = useState<SerieDeExame | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [painel, setPainel] = useState<"nenhum" | "registrar" | "solicitar">("nenhum");

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const [p, e, s] = await Promise.all([
        api.pacientes.buscar(pacienteId),
        api.exames.doPaciente(pacienteId),
        api.exames.solicitacoes(pacienteId),
      ]);
      setPaciente(p);
      setExames(e);
      setSolicitacoes(s);
    } catch (e) {
      setErro(explicarErro(e, "abrir os exames"));
    } finally {
      setCarregando(false);
    }
  }, [pacienteId]);

  useEffect(() => {
    void carregar();
    api.exames.parametros().then(setParametros).catch(() => setParametros([]));
  }, [carregar]);

  async function abrirSerie(parametroId: number) {
    try {
      setSerie(await api.exames.serie(pacienteId, parametroId));
    } catch {
      setSerie(null);
    }
  }

  async function anexarLaudo(exameId: number, arquivo: File) {
    try {
      await api.exames.anexarLaudo(exameId, arquivo);
      recado.confirmar("Laudo anexado ao resultado.");
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "anexar o laudo"));
    }
  }

  async function abrirLaudo(exameId: number) {
    try {
      const { url } = await api.exames.laudo(exameId);
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      setErro("Não foi possível abrir o laudo.");
    }
  }

  async function remover(exame: ExameResultado) {
    if (!confirm(`Remover ${exame.parametro} de ${formatarBr(exame.dataColeta)}?`)) return;
    try {
      await api.exames.remover(exame.id);
      recado.confirmar(`${exame.parametro} de ${formatarBr(exame.dataColeta)} removido.`);
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "remover o exame"));
    }
  }

  const alterados = exames.filter((e) => e.classificacao && e.classificacao !== "NORMAL");

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <Link to={`/pacientes/${pacienteId}`} className="minusculo">
            ← {paciente?.nome ?? "Paciente"}
          </Link>
          <h1>Exames</h1>
          <p>
            {contar(exames.length, "resultado", "resultados")}
            {alterados.length > 0 ? ` · ${alterados.length} fora da referência` : ""}
          </p>
        </div>
        <div className="linha">
          <button
            className="botao secundario"
            onClick={() => setPainel(painel === "solicitar" ? "nenhum" : "solicitar")}
          >
            Solicitar exames
          </button>
          <button
            className="botao"
            onClick={() => setPainel(painel === "registrar" ? "nenhum" : "registrar")}
          >
            Registrar resultado
          </button>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {painel === "registrar" && (
        <FormularioResultado
          pacienteId={pacienteId}
          parametros={parametros}
          aoFechar={() => setPainel("nenhum")}
          aoSalvar={async () => {
            setPainel("nenhum");
            await carregar();
          }}
        />
      )}

      {painel === "solicitar" && (
        <FormularioSolicitacao
          pacienteId={pacienteId}
          parametros={parametros}
          aoFechar={() => setPainel("nenhum")}
          aoSalvar={async () => {
            setPainel("nenhum");
            await carregar();
          }}
        />
      )}

      {serie && <PainelSerie serie={serie} aoFechar={() => setSerie(null)} />}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : exames.length === 0 ? (
        <div className="cartao vazio">
          Nenhum exame registrado. Use <strong>Registrar resultado</strong> quando o laudo chegar.
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Exame</th>
                <th>Coleta</th>
                <th className="num">Resultado</th>
                <th>Referência usada</th>
                <th>Laudo</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {exames.map((e) => (
                <tr key={e.id}>
                  <td>
                    <button
                      type="button"
                      className="ligacao"
                      onClick={() => abrirSerie(e.parametroId)}
                    >
                      <strong>{e.parametro}</strong>
                    </button>
                    {e.grupo && <div className="minusculo">{e.grupo}</div>}
                    {e.observacao && <div className="minusculo">{e.observacao}</div>}
                  </td>
                  <td className="mono">{formatarBr(e.dataColeta)}</td>
                  <td className="num">
                    {e.valor === undefined ? (
                      <span className="minusculo">não determinado</span>
                    ) : (
                      <>
                        {e.valor.toLocaleString("pt-BR")} {e.unidade}
                        {e.classificacao && (
                          <div>
                            <span
                              className={`etiqueta ${CLASSE_POR_CLASSIFICACAO[e.classificacao] ?? ""}`}
                            >
                              {e.classificacaoDescricao}
                            </span>
                          </div>
                        )}
                      </>
                    )}
                  </td>
                  <td className="discreto">{e.referencia ?? "—"}</td>
                  <td>
                    <BotaoLaudo
                      exame={e}
                      aoAnexar={(arquivo) => anexarLaudo(e.id, arquivo)}
                      aoAbrir={() => abrirLaudo(e.id)}
                    />
                  </td>
                  <td>
                    <button className="botao perigo pequeno" onClick={() => remover(e)}>
                      Remover
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {solicitacoes.length > 0 && (
        <div className="cartao" style={{ marginTop: "0.9rem" }}>
          <h2>Solicitações</h2>
          {solicitacoes.map((s) => (
            <div key={s.id} style={{ marginTop: "0.6rem" }}>
              <span className="mono">{formatarBr(s.data)}</span>{" "}
              <span className="discreto">{s.exames.join(", ")}</span>
              {s.observacao && <div className="minusculo">{s.observacao}</div>}
            </div>
          ))}
        </div>
      )}
    </>
  );
}

function BotaoLaudo({
  exame,
  aoAnexar,
  aoAbrir,
}: {
  exame: ExameResultado;
  aoAnexar: (arquivo: File) => void;
  aoAbrir: () => void;
}) {
  const entrada = useRef<HTMLInputElement>(null);

  return (
    <>
      {exame.temLaudo ? (
        <button className="botao secundario pequeno" onClick={aoAbrir}>
          Abrir
        </button>
      ) : (
        <button className="botao secundario pequeno" onClick={() => entrada.current?.click()}>
          Anexar
        </button>
      )}
      <input
        ref={entrada}
        type="file"
        accept=".pdf,image/*"
        style={{ display: "none" }}
        onChange={(e) => {
          const arquivo = e.target.files?.[0];
          if (arquivo) aoAnexar(arquivo);
          e.target.value = "";
        }}
      />
    </>
  );
}

function PainelSerie({ serie, aoFechar }: { serie: SerieDeExame; aoFechar: () => void }) {
  return (
    <div className="cartao" style={{ marginBottom: "0.9rem" }}>
      <div className="linha" style={{ justifyContent: "space-between" }}>
        <h2>{serie.parametro} ao longo do tempo</h2>
        <button className="botao secundario pequeno" onClick={aoFechar}>
          Fechar
        </button>
      </div>

      {serie.unidadesMisturadas && (
        <div className="aviso atencao" style={{ marginTop: "0.7rem" }}>
          Esta série tem coletas em unidades diferentes. Os valores não são comparáveis entre si, e
          a variação não é calculada entre eles.
        </div>
      )}

      <div className="regua-dia" style={{ marginTop: "0.8rem" }}>
        {serie.pontos.map((p, i) => (
          <div className="regua-item" key={i}>
            <span className="regua-hora sem-hora">{formatarBr(p.dataColeta).slice(0, 5)}</span>
            <div className="regua-corpo">
              <strong style={{ fontSize: "1rem" }}>
                {p.valor === undefined ? "não determinado" : `${p.valor.toLocaleString("pt-BR")} ${p.unidade}`}
              </strong>
              {p.classificacao && (
                <span
                  className={`etiqueta ${CLASSE_POR_CLASSIFICACAO[p.classificacao] ?? ""}`}
                  style={{ marginLeft: "0.5rem" }}
                >
                  {p.classificacao.toLowerCase()}
                </span>
              )}
              {p.variacao !== undefined && (
                <p className="atendimento-meta">
                  {p.variacao > 0 ? "+" : ""}
                  {p.variacao.toLocaleString("pt-BR")} desde a coleta anterior
                </p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function FormularioResultado({
  pacienteId,
  parametros,
  aoFechar,
  aoSalvar,
}: {
  pacienteId: number;
  parametros: ParametroExame[];
  aoFechar: () => void;
  aoSalvar: () => Promise<void>;
}) {
  const [parametroId, setParametroId] = useState("");
  const [dataColeta, setDataColeta] = useState(hojeIso());
  const [valor, setValor] = useState("");
  const [unidade, setUnidade] = useState("");
  const [observacao, setObservacao] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const escolhido = parametros.find((p) => String(p.id) === parametroId);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setSalvando(true);
    try {
      await api.exames.registrar(pacienteId, {
        parametroId: Number(parametroId),
        dataColeta,
        valor: valor ? Number(valor.replace(",", ".")) : undefined,
        unidade: unidade.trim() || undefined,
        observacao: observacao.trim() || undefined,
      });
      await aoSalvar();
    } catch (e) {
      setErro(explicarErro(e, "registrar o resultado"));
      setSalvando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Registrar resultado</h2>

      {erro && (
        <div className="aviso erro" style={{ margin: "0.8rem 0" }}>
          {erro}
        </div>
      )}

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <div className="campo" style={{ gridColumn: "span 2" }}>
          <label htmlFor="ex-parametro">Exame</label>
          <select
            id="ex-parametro"
            value={parametroId}
            onChange={(e) => {
              setParametroId(e.target.value);
              setUnidade("");
            }}
            required
          >
            <option value="">Selecione…</option>
            {parametros.map((p) => (
              <option key={p.id} value={p.id}>
                {p.grupo ? `${p.grupo} · ` : ""}
                {p.nome}
              </option>
            ))}
          </select>
          {escolhido && escolhido.faixas.length > 0 && (
            <span className="minusculo">
              Referência: {escolhido.faixas.map((f) => f.texto).join(" · ")} {escolhido.unidadePadrao}
            </span>
          )}
        </div>
        <div className="campo">
          <label htmlFor="ex-data">Data da coleta</label>
          <input
            id="ex-data"
            type="date"
            value={dataColeta}
            max={hojeIso()}
            onChange={(e) => setDataColeta(e.target.value)}
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="ex-valor">Resultado</label>
          <input
            id="ex-valor"
            inputMode="decimal"
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            placeholder={escolhido?.unidadePadrao}
          />
          <span className="minusculo">
            Deixe vazio se o exame foi pedido e ainda não saiu.
          </span>
        </div>
        <div className="campo">
          <label htmlFor="ex-unidade">Unidade</label>
          <input
            id="ex-unidade"
            value={unidade}
            onChange={(e) => setUnidade(e.target.value)}
            placeholder={escolhido?.unidadePadrao ?? ""}
          />
          <span className="minusculo">
            Só se o laudo usar outra. Nesse caso o valor não é classificado.
          </span>
        </div>
        <div className="campo">
          <label htmlFor="ex-obs">Observação</label>
          <input id="ex-obs" value={observacao} onChange={(e) => setObservacao(e.target.value)} />
        </div>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={salvando}>
          {salvando ? "Salvando…" : "Registrar"}
        </button>
      </div>
    </form>
  );
}

function FormularioSolicitacao({
  pacienteId,
  parametros,
  aoFechar,
  aoSalvar,
}: {
  pacienteId: number;
  parametros: ParametroExame[];
  aoFechar: () => void;
  aoSalvar: () => Promise<void>;
}) {
  const [escolhidos, setEscolhidos] = useState<number[]>([]);
  const [data, setData] = useState(hojeIso());
  const [observacao, setObservacao] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  function alternar(id: number) {
    setEscolhidos((atual) =>
      atual.includes(id) ? atual.filter((x) => x !== id) : [...atual, id],
    );
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (escolhidos.length === 0) {
      setErro("Escolha ao menos um exame.");
      return;
    }
    setErro(null);
    setSalvando(true);
    try {
      await api.exames.solicitar(pacienteId, {
        data,
        parametroIds: escolhidos,
        observacao: observacao.trim() || undefined,
      });
      await aoSalvar();
    } catch (e) {
      setErro(explicarErro(e, "registrar a solicitação"));
      setSalvando(false);
    }
  }

  const porGrupo = new Map<string, ParametroExame[]>();
  parametros.forEach((p) => {
    const chave = p.grupo ?? "Outros";
    porGrupo.set(chave, [...(porGrupo.get(chave) ?? []), p]);
  });

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Solicitar exames</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        Registra o pedido entregue ao paciente. Os resultados são lançados quando o laudo chegar.
      </p>

      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.8rem" }}>
          {erro}
        </div>
      )}

      <div className="grade duas">
        {[...porGrupo.entries()].map(([grupo, lista]) => (
          <div key={grupo}>
            <span className="minusculo">{grupo}</span>
            {lista.map((p) => (
              <label className="linha" key={p.id} style={{ gap: "0.4rem", marginTop: "0.2rem" }}>
                <input
                  type="checkbox"
                  checked={escolhidos.includes(p.id)}
                  onChange={() => alternar(p.id)}
                  style={{ width: "auto" }}
                />
                <span style={{ fontSize: "0.88rem" }}>{p.nome}</span>
              </label>
            ))}
          </div>
        ))}
      </div>

      <div className="linha" style={{ marginTop: "0.9rem" }}>
        <div className="campo" style={{ width: 170 }}>
          <label htmlFor="sol-data">Data</label>
          <input
            id="sol-data"
            type="date"
            value={data}
            max={hojeIso()}
            onChange={(e) => setData(e.target.value)}
            required
          />
        </div>
        <div className="campo" style={{ flex: 1, minWidth: 220 }}>
          <label htmlFor="sol-obs">Observação</label>
          <input
            id="sol-obs"
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
            placeholder="Jejum de 8 horas."
          />
        </div>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={salvando}>
          {salvando ? "Salvando…" : `Solicitar ${contar(escolhidos.length, "exame", "exames")}`}
        </button>
      </div>
    </form>
  );
}
