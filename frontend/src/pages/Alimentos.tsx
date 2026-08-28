import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import type { AlimentoResumo, FonteDeDados, ResultadoImportacao } from "../api/types";
import { contar } from "../texto";

const FONTES: { valor: FonteDeDados | ""; rotulo: string }[] = [
  { valor: "", rotulo: "Todas as fontes" },
  { valor: "TACO", rotulo: "TACO — NEPA/Unicamp" },
  { valor: "OPEN_FOOD_FACTS", rotulo: "Open Food Facts" },
  { valor: "PERSONALIZADO", rotulo: "Cadastro próprio" },
  { valor: "RECEITA", rotulo: "Receitas" },
];

export default function Alimentos() {
  const navegar = useNavigate();

  const [alimentos, setAlimentos] = useState<AlimentoResumo[]>([]);
  const [total, setTotal] = useState(0);
  const [grupos, setGrupos] = useState<string[]>([]);
  const [termo, setTermo] = useState("");
  const [grupo, setGrupo] = useState("");
  const [fonte, setFonte] = useState<FonteDeDados | "">("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [painel, setPainel] = useState<"nenhum" | "novo" | "importar">("nenhum");
  const [porCodigo, setPorCodigo] = useState(false);

  useEffect(() => {
    api.alimentos.grupos().then(setGrupos).catch(() => setGrupos([]));
  }, []);

  const buscar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      // Uma sequência só de dígitos com tamanho de EAN é código de barras, e
      // não nome de alimento: a busca textual devolveria vazio, porque nenhum
      // produto se chama "7890000110266". Quem digita isso está com a
      // embalagem na mão.
      const codigo = termo.replace(/[\s.-]/g, "");
      if (/^\d{8,14}$/.test(codigo)) {
        const achados = await api.alimentos.porCodigoDeBarras(codigo);
        setPorCodigo(true);
        setAlimentos(
          achados.map((a) => ({
            id: a.id,
            descricao: a.descricao,
            grupo: a.grupo,
            fonte: a.fonte,
            marca: a.marca,
            basePublica: a.basePublica,
            energiaKcal: a.composicao.energiaKcal,
            proteinaG: a.composicao.proteinaG,
            carboidratoG: a.composicao.carboidratoG,
            lipideosG: a.composicao.lipideosG,
          })),
        );
        setTotal(achados.length);
        return;
      }

      setPorCodigo(false);
      const pagina = await api.alimentos.buscar({
        termo: termo.trim() || undefined,
        grupo: grupo || undefined,
        fonte: fonte || undefined,
        size: 40,
      });
      setAlimentos(pagina.content);
      setTotal(pagina.totalElements);
    } catch (e) {
      setErro(explicarErro(e, "buscar alimentos"));
    } finally {
      setCarregando(false);
    }
  }, [termo, grupo, fonte]);

  useEffect(() => {
    const relogio = setTimeout(buscar, termo ? 300 : 0);
    return () => clearTimeout(relogio);
  }, [buscar, termo]);

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Alimentos</h1>
          <p>
            {total.toLocaleString("pt-BR")} {total === 1 ? "alimento" : "alimentos"} no acervo
            visível
          </p>
        </div>
        <div className="linha">
          <button className="botao secundario" onClick={() => setPainel(painel === "importar" ? "nenhum" : "importar")}>
            Importar tabela
          </button>
          <button className="botao secundario" onClick={() => navegar("/receitas/novo")}>
            Nova receita
          </button>
          <button className="botao" onClick={() => setPainel(painel === "novo" ? "nenhum" : "novo")}>
            Novo alimento
          </button>
        </div>
      </div>

      {painel === "importar" && <PainelImportacao aoFechar={() => setPainel("nenhum")} aoImportar={buscar} />}
      {painel === "novo" && (
        <FormularioNovoAlimento
          aoFechar={() => setPainel("nenhum")}
          aoCriar={(id) => navegar(`/alimentos/${id}`)}
        />
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="campo" style={{ flex: 2, minWidth: 220 }}>
            <label htmlFor="busca-alimento">Buscar</label>
            <input
              id="busca-alimento"
              value={termo}
              onChange={(e) => setTermo(e.target.value)}
              placeholder="Digite sem acento, se preferir: acucar, feijao…"
            />
          </div>
          <div className="campo" style={{ flex: 1, minWidth: 170 }}>
            <label htmlFor="filtro-grupo">Grupo</label>
            <select id="filtro-grupo" value={grupo} onChange={(e) => setGrupo(e.target.value)}>
              <option value="">Todos os grupos</option>
              {grupos.map((g) => (
                <option key={g} value={g}>
                  {g}
                </option>
              ))}
            </select>
          </div>
          <div className="campo" style={{ flex: 1, minWidth: 170 }}>
            <label htmlFor="filtro-fonte">Fonte</label>
            <select
              id="filtro-fonte"
              value={fonte}
              onChange={(e) => setFonte(e.target.value as FonteDeDados | "")}
            >
              {FONTES.map((f) => (
                <option key={f.valor} value={f.valor}>
                  {f.rotulo}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : alimentos.length === 0 ? (
        <div className="cartao vazio">
          {porCodigo
            ? `Nenhum produto com o código ${termo.trim()}. A base cobre o que o Open Food Facts tem do Brasil — cadastre o produto para tê-lo aqui.`
            : "Nenhum alimento com esses filtros. Tente um termo mais curto ou remova o filtro de fonte."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Alimento</th>
                <th>Grupo</th>
                <th className="num">kcal</th>
                <th className="num">Prot.</th>
                <th className="num">Carb.</th>
                <th className="num">Gord.</th>
                <th>Fonte</th>
              </tr>
            </thead>
            <tbody>
              {alimentos.map((a) => (
                <tr
                  key={a.id}
                  className="clicavel"
                  onClick={() =>
                    /* Receita abre no editor dela: a ficha de alimento não
                       mostraria os ingredientes, que é o que se vai conferir. */
                    navegar(a.fonte === "RECEITA" ? `/receitas/${a.id}` : `/alimentos/${a.id}`)
                  }
                >
                  <td>
                    <strong>{a.descricao}</strong>
                    {a.marca && <div className="minusculo">{a.marca}</div>}
                  </td>
                  <td className="discreto">{a.grupo ?? "—"}</td>
                  <td className="num">{num(a.energiaKcal)}</td>
                  <td className="num">{num(a.proteinaG)}</td>
                  <td className="num">{num(a.carboidratoG)}</td>
                  <td className="num">{num(a.lipideosG)}</td>
                  <td>
                    <span className={`etiqueta ${a.basePublica ? "" : "verde"}`}>
                      {a.fonte === "OPEN_FOOD_FACTS" ? "OFF" : a.fonte.toLowerCase()}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="minusculo" style={{ marginTop: "0.7rem" }}>
        Valores por 100 g. Um traço indica nutriente não determinado na fonte — não é zero.
      </p>
    </>
  );
}

function num(valor?: number) {
  return valor === undefined || valor === null ? "—" : valor.toFixed(valor >= 100 ? 0 : 1).replace(".", ",");
}

function PainelImportacao({
  aoFechar,
  aoImportar,
}: {
  aoFechar: () => void;
  aoImportar: () => void;
}) {
  const entrada = useRef<HTMLInputElement>(null);
  const [fonte, setFonte] = useState<FonteDeDados>("PERSONALIZADO");
  const [separador, setSeparador] = useState(",");
  const [resultado, setResultado] = useState<ResultadoImportacao | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    const arquivo = entrada.current?.files?.[0];
    if (!arquivo) {
      setErro("Escolha um arquivo CSV.");
      return;
    }
    setErro(null);
    setResultado(null);
    setEnviando(true);
    try {
      const saida = await api.alimentos.importar(arquivo, fonte, separador);
      setResultado(saida);
      aoImportar();
    } catch (e) {
      setErro(explicarErro(e, "importar o arquivo"));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Importar tabela de alimentos</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        As colunas de nutriente são reconhecidas pelo nome: <code>energiaKcal</code>,{" "}
        <code>energia_kcal</code> ou <code>Energia (kcal)</code> chegam todas ao mesmo campo.
        Os alimentos ficam vinculados ao seu consultório e não entram na base comum.
      </p>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.85rem" }}>{erro}</div>}

      {resultado && (
        <div className={`aviso ${resultado.importados > 0 ? "ok" : "atencao"}`} style={{ marginBottom: "0.85rem" }}>
          <strong>
            {contar(resultado.importados, "alimento importado", "alimentos importados")}
            {resultado.ignorados > 0
              ? `, ${contar(resultado.ignorados, "linha ignorada", "linhas ignoradas")}`
              : ""}
            .
          </strong>
          {resultado.avisos.length > 0 && (
            <ul style={{ margin: "0.4rem 0 0", paddingLeft: "1.1rem" }}>
              {resultado.avisos.slice(0, 6).map((aviso, i) => (
                <li key={i}>{aviso}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="grade tres">
        <div className="campo">
          <label htmlFor="imp-arquivo">Arquivo CSV</label>
          <input id="imp-arquivo" type="file" accept=".csv,text/csv" ref={entrada} />
        </div>
        <div className="campo">
          <label htmlFor="imp-fonte">Procedência</label>
          <select
            id="imp-fonte"
            value={fonte}
            onChange={(e) => setFonte(e.target.value as FonteDeDados)}
          >
            <option value="PERSONALIZADO">Cadastro próprio</option>
            <option value="TBCA">TBCA — FoRC/USP</option>
            <option value="IBGE">IBGE — POF</option>
            <option value="FABRICANTE">Rótulo do fabricante</option>
          </select>
        </div>
        <div className="campo">
          <label htmlFor="imp-sep">Separador</label>
          <select id="imp-sep" value={separador} onChange={(e) => setSeparador(e.target.value)}>
            <option value=",">Vírgula ( , )</option>
            <option value=";">Ponto e vírgula ( ; )</option>
            <option value={"\t"}>Tabulação</option>
          </select>
        </div>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Fechar
        </button>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Importando…" : "Importar"}
        </button>
      </div>
    </form>
  );
}

function FormularioNovoAlimento({
  aoFechar,
  aoCriar,
}: {
  aoFechar: () => void;
  aoCriar: (id: number) => void;
}) {
  const [descricao, setDescricao] = useState("");
  const [grupo, setGrupo] = useState("");
  const [marca, setMarca] = useState("");
  const [valores, setValores] = useState<Record<string, string>>({});
  const [medidaDescricao, setMedidaDescricao] = useState("");
  const [medidaGramas, setMedidaGramas] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const errosDeCampo = useErrosDeCampo();
  const recado = useRecado();

  const campos = [
    { chave: "energiaKcal", rotulo: "Energia (kcal)" },
    { chave: "proteinaG", rotulo: "Proteínas (g)" },
    { chave: "carboidratoG", rotulo: "Carboidratos (g)" },
    { chave: "lipideosG", rotulo: "Gorduras (g)" },
    { chave: "fibraG", rotulo: "Fibra (g)" },
    { chave: "acucaresG", rotulo: "Açúcares (g)" },
    { chave: "gordurasSaturadasG", rotulo: "Saturadas (g)" },
    { chave: "sodioMg", rotulo: "Sódio (mg)" },
  ];

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      // Campo em branco fica de fora do corpo: ausência é "não determinado",
      // e enviar zero afirmaria que o alimento não contém o nutriente.
      const composicao: Record<string, number> = {};
      Object.entries(valores).forEach(([chave, texto]) => {
        const limpo = texto.replace(",", ".").trim();
        if (limpo !== "" && !Number.isNaN(Number(limpo))) {
          composicao[chave] = Number(limpo);
        }
      });

      const medidas =
        medidaDescricao.trim() && medidaGramas.trim()
          ? [
              {
                descricao: medidaDescricao.trim(),
                gramas: Number(medidaGramas.replace(",", ".")),
                padrao: true,
              },
            ]
          : [];

      const criado = await api.alimentos.criar({
        descricao: descricao.trim(),
        grupo: grupo.trim() || undefined,
        marca: marca.trim() || undefined,
        composicao,
        medidas,
      });
      recado.confirmar(`"${criado.descricao}" entrou na sua base de alimentos.`);
      aoCriar(criado.id);
    } catch (e) {
      if (!errosDeCampo.aplicar(e)) {
        setErro(explicarErro(e, "cadastrar o alimento"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Novo alimento</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        Valores por 100 g. Deixe em branco o que não souber — em branco significa
        “não determinado”, e é diferente de zero.
      </p>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.85rem" }}>{erro}</div>}

      <div className="grade tres">
        <div className="campo" style={{ gridColumn: "span 2" }}>
          <label htmlFor="na-desc">Descrição</label>
          <input
            id="na-desc"
            name="descricao"
            value={descricao}
            onChange={(e) => setDescricao(e.target.value)}
            required
            {...errosDeCampo.props("descricao")}
          />
          <ErroDeCampo campo="descricao" erros={errosDeCampo.erros} />
        </div>
        <div className="campo">
          <label htmlFor="na-grupo">Grupo</label>
          <input
            id="na-grupo"
            name="grupo"
            value={grupo}
            onChange={(e) => setGrupo(e.target.value)}
            {...errosDeCampo.props("grupo")}
          />
          <ErroDeCampo campo="grupo" erros={errosDeCampo.erros} />
        </div>
        <div className="campo">
          <label htmlFor="na-marca">Marca</label>
          <input
            id="na-marca"
            name="marca"
            value={marca}
            onChange={(e) => setMarca(e.target.value)}
            {...errosDeCampo.props("marca")}
          />
          <ErroDeCampo campo="marca" erros={errosDeCampo.erros} />
        </div>
      </div>

      <h3 style={{ margin: "1rem 0 0.5rem" }}>Composição por 100 g</h3>
      <div className="grade tres">
        {campos.map((campo) => (
          <div className="campo" key={campo.chave}>
            <label htmlFor={`na-${campo.chave}`}>{campo.rotulo}</label>
            <input
              id={`na-${campo.chave}`}
              inputMode="decimal"
              value={valores[campo.chave] ?? ""}
              onChange={(e) => setValores((v) => ({ ...v, [campo.chave]: e.target.value }))}
            />
          </div>
        ))}
      </div>

      <h3 style={{ margin: "1rem 0 0.5rem" }}>Porção usual (opcional)</h3>
      <div className="grade duas">
        <div className="campo">
          <label htmlFor="na-med">Descrição da porção</label>
          <input
            id="na-med"
            placeholder="colher de sopa, fatia, unidade…"
            value={medidaDescricao}
            onChange={(e) => setMedidaDescricao(e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="na-med-g">Peso (g)</label>
          <input
            id="na-med-g"
            inputMode="decimal"
            value={medidaGramas}
            onChange={(e) => setMedidaGramas(e.target.value)}
          />
        </div>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Salvando…" : "Cadastrar alimento"}
        </button>
      </div>
    </form>
  );
}
