import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { formatarBr, hojeIso, primeiroDiaDoMesIso, ultimoDiaDoMesIso } from "../api/datas";
import type {
  Apuracao,
  Lancamento,
  PacienteResumo,
  Recibo,
  SituacaoLancamento,
  TipoLancamento,
} from "../api/types";
import { contar } from "../texto";

const CATEGORIAS_RECEITA = ["Consulta", "Retorno", "Avaliação", "Pacote", "Outros"];
const CATEGORIAS_DESPESA = ["Aluguel", "Material", "Software", "Impostos", "Marketing", "Outros"];

export default function Financeiro() {
  const [de, setDe] = useState(primeiroDiaDoMesIso());
  const [ate, setAte] = useState(ultimoDiaDoMesIso());

  const [apuracao, setApuracao] = useState<Apuracao | null>(null);
  const [lancamentos, setLancamentos] = useState<Lancamento[]>([]);
  const [vencidos, setVencidos] = useState<Lancamento[]>([]);
  const [pacientes, setPacientes] = useState<PacienteResumo[]>([]);
  const [filtroSituacao, setFiltroSituacao] = useState<SituacaoLancamento | "">("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [criando, setCriando] = useState(false);
  const [recibo, setRecibo] = useState<Recibo | null>(null);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const [resultado, pagina, atrasados] = await Promise.all([
        api.financeiro.apurar(de, ate),
        api.financeiro.listar({
          de,
          ate,
          situacao: filtroSituacao || undefined,
          size: 100,
        }),
        api.financeiro.vencidos(),
      ]);
      setApuracao(resultado);
      setLancamentos(pagina.content);
      setVencidos(atrasados);
    } catch (e) {
      setErro(explicarErro(e, "abrir o financeiro"));
    } finally {
      setCarregando(false);
    }
  }, [de, ate, filtroSituacao]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  useEffect(() => {
    api.pacientes.listar({ ativo: true, size: 200 })
      .then((p) => setPacientes(p.content))
      .catch(() => setPacientes([]));
  }, []);

  async function acao(executar: () => Promise<unknown>) {
    setErro(null);
    try {
      await executar();
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "concluir a operação"));
    }
  }

  async function abrirRecibo(id: number) {
    setErro(null);
    try {
      setRecibo(await api.financeiro.recibo(id));
    } catch (e) {
      setErro(explicarErro(e, "emitir o recibo"));
    }
  }

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Financeiro</h1>
          <p>Lançamentos, inadimplência e apuração do período.</p>
        </div>
        <button className="botao" onClick={() => setCriando((v) => !v)}>
          {criando ? "Cancelar" : "Novo lançamento"}
        </button>
      </div>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      {recibo && <PainelRecibo recibo={recibo} aoFechar={() => setRecibo(null)} />}

      {criando && (
        <FormularioLancamento
          pacientes={pacientes}
          aoSalvar={async () => {
            setCriando(false);
            await carregar();
          }}
        />
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="campo" style={{ width: 165 }}>
            <label htmlFor="fin-de">Competência de</label>
            <input id="fin-de" type="date" value={de} onChange={(e) => setDe(e.target.value)} />
          </div>
          <div className="campo" style={{ width: 165 }}>
            <label htmlFor="fin-ate">até</label>
            <input id="fin-ate" type="date" value={ate} onChange={(e) => setAte(e.target.value)} />
          </div>
          <div className="campo" style={{ width: 175 }}>
            <label htmlFor="fin-sit">Situação</label>
            <select
              id="fin-sit"
              value={filtroSituacao}
              onChange={(e) => setFiltroSituacao(e.target.value as SituacaoLancamento | "")}
            >
              <option value="">Todas</option>
              <option value="PENDENTE">Pendente</option>
              <option value="PAGO">Pago</option>
              <option value="CANCELADO">Cancelado</option>
            </select>
          </div>
        </div>
      </div>

      {apuracao && <PainelApuracao apuracao={apuracao} />}

      {vencidos.length > 0 && (
        <div className="aviso atencao" style={{ marginBottom: "0.9rem" }}>
          <strong>
            {contar(vencidos.length, "lançamento vencido", "lançamentos vencidos")}, somando {moeda(
              vencidos.reduce((soma, l) => soma + l.valor, 0),
            )}
            .
          </strong>{" "}
          {vencidos
            .slice(0, 3)
            .map((l) => `${l.pacienteNome ?? l.categoria} (${formatarBr(l.vencimento)})`)
            .join(", ")}
          {vencidos.length > 3 ? " e outros." : "."}
        </div>
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : lancamentos.length === 0 ? (
        <div className="cartao vazio">
          Nenhum lançamento nesta competência. Registre uma cobrança para acompanhar o caixa do mês.
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Competência</th>
                <th>Descrição</th>
                <th>Paciente</th>
                <th className="num">Valor</th>
                <th>Situação</th>
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {lancamentos.map((l) => (
                <tr key={l.id}>
                  <td className="mono">{formatarBr(l.competencia)}</td>
                  <td>
                    <strong>{l.categoria}</strong>
                    {l.descricao && <div className="minusculo">{l.descricao}</div>}
                  </td>
                  <td className="discreto">{l.pacienteNome ?? "—"}</td>
                  <td
                    className="num"
                    style={{ color: l.tipo === "RECEITA" ? "var(--accent)" : "var(--erro)" }}
                  >
                    {l.tipo === "RECEITA" ? "+" : "−"} {moeda(l.valor)}
                  </td>
                  <td>
                    <span
                      className={`etiqueta ${
                        l.situacao === "PAGO" ? "verde" : l.vencido ? "vermelha" : "ambar"
                      }`}
                    >
                      {l.vencido ? "vencido" : l.situacaoDescricao.toLowerCase()}
                    </span>
                    {l.dataPagamento && (
                      <div className="minusculo">pago em {formatarBr(l.dataPagamento)}</div>
                    )}
                  </td>
                  <td>
                    <div className="linha" style={{ gap: "0.3rem" }}>
                      {l.situacao === "PENDENTE" && (
                        <>
                          <button
                            className="botao pequeno"
                            onClick={() => acao(() => api.financeiro.pagar(l.id))}
                          >
                            Dar baixa
                          </button>
                          <button
                            className="botao secundario pequeno"
                            onClick={() => acao(() => api.financeiro.cancelar(l.id))}
                          >
                            Cancelar
                          </button>
                        </>
                      )}
                      {l.situacao === "PAGO" && (
                        <>
                          {l.tipo === "RECEITA" && (
                            <button
                              className="botao secundario pequeno"
                              onClick={() => abrirRecibo(l.id)}
                            >
                              Recibo
                            </button>
                          )}
                          <button
                            className="botao secundario pequeno"
                            onClick={() => acao(() => api.financeiro.estornar(l.id))}
                          >
                            Estornar
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/**
 * Efetivado e previsto aparecem lado a lado, nunca somados.
 * Um número só misturaria dinheiro que entrou com dinheiro que talvez entre.
 */
function PainelApuracao({ apuracao }: { apuracao: Apuracao }) {
  return (
    <div className="cartao" style={{ marginBottom: "0.9rem" }}>
      <h2>Apuração do período</h2>

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <Valor rotulo="Recebido" valor={apuracao.totalRecebido} cor="var(--accent)" />
        <Valor rotulo="A receber" valor={apuracao.totalAReceber} cor="var(--alerta)" />
        <Valor rotulo="Despesas pagas" valor={apuracao.despesasPagas} cor="var(--erro)" />
        <Valor rotulo="Despesas a pagar" valor={apuracao.despesasAPagar} cor="var(--ink-soft)" />
        <Valor
          rotulo="Resultado efetivado"
          valor={apuracao.resultadoEfetivado}
          cor={apuracao.resultadoEfetivado >= 0 ? "var(--accent)" : "var(--erro)"}
          destaque
        />
        <Valor
          rotulo="Resultado previsto"
          valor={apuracao.resultadoPrevisto}
          cor="var(--ink-soft)"
        />
      </div>

      <p className="minusculo" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
        O resultado efetivado conta apenas o que já entrou e saiu. O previsto inclui pendências —
        são números diferentes de propósito.
      </p>
    </div>
  );
}

function Valor({
  rotulo,
  valor,
  cor,
  destaque,
}: {
  rotulo: string;
  valor: number;
  cor: string;
  destaque?: boolean;
}) {
  return (
    <div>
      <span className="minusculo">{rotulo}</span>
      <div
        className="mono"
        style={{ fontSize: destaque ? "1.3rem" : "1.1rem", fontWeight: 700, color: cor }}
      >
        {moeda(valor)}
      </div>
    </div>
  );
}

function PainelRecibo({ recibo, aoFechar }: { recibo: Recibo; aoFechar: () => void }) {
  return (
    <div className="cartao" style={{ marginBottom: "0.9rem", borderColor: "var(--accent)" }}>
      <div className="linha" style={{ justifyContent: "space-between" }}>
        <h2>Recibo</h2>
        <button className="botao secundario pequeno" onClick={aoFechar}>
          Fechar
        </button>
      </div>

      <div style={{ marginTop: "0.9rem", lineHeight: 1.8 }}>
        <p style={{ margin: 0 }}>
          Recebi de <strong>{recibo.pagadorNome ?? "—"}</strong> a importância de{" "}
          <strong>{moeda(recibo.valor)}</strong> ({recibo.valorPorExtenso}), referente a{" "}
          <strong>{recibo.referente}</strong>.
        </p>
        <p style={{ margin: "0.8rem 0 0" }} className="discreto">
          {recibo.consultorioNome} · {recibo.profissionalNome}
          {recibo.profissionalCrn ? ` · ${recibo.profissionalCrn}` : ""}
          <br />
          Pagamento em {formatarBr(recibo.dataPagamento)} · Emitido em {formatarBr(recibo.emitidoEm)}
        </p>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button className="botao secundario pequeno" onClick={() => window.print()}>
          Imprimir
        </button>
      </div>
    </div>
  );
}

function FormularioLancamento({
  pacientes,
  aoSalvar,
}: {
  pacientes: PacienteResumo[];
  aoSalvar: () => Promise<void>;
}) {
  const hoje = hojeIso();

  const [tipo, setTipo] = useState<TipoLancamento>("RECEITA");
  const [valor, setValor] = useState("");
  const [competencia, setCompetencia] = useState(hoje);
  const [vencimento, setVencimento] = useState(hoje);
  const [categoria, setCategoria] = useState("Consulta");
  const [pacienteId, setPacienteId] = useState("");
  const [formaPagamento, setFormaPagamento] = useState("");
  const [descricao, setDescricao] = useState("");
  const [jaPago, setJaPago] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const campos = useErrosDeCampo();
  const recado = useRecado();

  const categorias = tipo === "RECEITA" ? CATEGORIAS_RECEITA : CATEGORIAS_DESPESA;

  function trocarTipo(novo: TipoLancamento) {
    setTipo(novo);
    setCategoria(novo === "RECEITA" ? CATEGORIAS_RECEITA[0]! : CATEGORIAS_DESPESA[0]!);
    if (novo === "DESPESA") setPacienteId("");
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      const criado = await api.financeiro.criar({
        tipo,
        // O valor é sempre positivo: quem define entrada ou saída é o tipo.
        valor: Number(valor.replace(",", ".")),
        competencia,
        vencimento: vencimento || undefined,
        categoria,
        formaPagamento: formaPagamento.trim() || undefined,
        descricao: descricao.trim() || undefined,
        pacienteId: pacienteId ? Number(pacienteId) : undefined,
      });
      if (jaPago) {
        await api.financeiro.pagar(criado.id, hoje);
      }
      recado.confirmar(
        `${tipo === "RECEITA" ? "Receita" : "Despesa"} de R$ ${valor} registrada${jaPago ? " e marcada como paga" : ""}.`,
      );
      await aoSalvar();
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "registrar o lançamento"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Novo lançamento</h2>

      {erro && <div className="aviso erro" style={{ margin: "0.8rem 0" }}>{erro}</div>}

      <div className="linha" style={{ marginTop: "0.8rem", gap: "0.3rem" }}>
        <button
          type="button"
          className={`botao ${tipo === "RECEITA" ? "" : "secundario"} pequeno`}
          onClick={() => trocarTipo("RECEITA")}
        >
          Receita
        </button>
        <button
          type="button"
          className={`botao ${tipo === "DESPESA" ? "" : "secundario"} pequeno`}
          onClick={() => trocarTipo("DESPESA")}
        >
          Despesa
        </button>
      </div>

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <div className="campo">
          <label htmlFor="fl-valor">Valor (R$)</label>
          <input
            id="fl-valor"
            name="valor"
            inputMode="decimal"
            value={valor}
            onChange={(e) => setValor(e.target.value)}
            required
            {...campos.props("valor")}
          />
          <ErroDeCampo campo="valor" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="fl-cat">Categoria</label>
          <select
            id="fl-cat"
            name="categoria"
            value={categoria}
            onChange={(e) => setCategoria(e.target.value)}
            {...campos.props("categoria")}
          >
            {categorias.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <ErroDeCampo campo="categoria" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="fl-forma">Forma de pagamento</label>
          <input
            id="fl-forma"
            name="formaPagamento"
            value={formaPagamento}
            onChange={(e) => setFormaPagamento(e.target.value)}
            placeholder="Pix, cartão, dinheiro…"
            {...campos.props("formaPagamento")}
          />
          <ErroDeCampo campo="formaPagamento" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="fl-comp">Competência</label>
          <input
            id="fl-comp"
            name="competencia"
            type="date"
            value={competencia}
            onChange={(e) => setCompetencia(e.target.value)}
            required
            {...campos.props("competencia")}
          />
          <ErroDeCampo campo="competencia" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="fl-venc">Vencimento</label>
          <input
            id="fl-venc"
            name="vencimento"
            type="date"
            value={vencimento}
            onChange={(e) => setVencimento(e.target.value)}
            {...campos.props("vencimento")}
          />
          <ErroDeCampo campo="vencimento" erros={campos.erros} />
        </div>
        {tipo === "RECEITA" && (
          <div className="campo">
            <label htmlFor="fl-paciente">Paciente</label>
            <select
              id="fl-paciente"
              value={pacienteId}
              onChange={(e) => setPacienteId(e.target.value)}
            >
              <option value="">Nenhum</option>
              {pacientes.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      <div className="campo" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="fl-desc">Descrição</label>
        <input
          id="fl-desc"
          name="descricao"
          value={descricao}
          onChange={(e) => setDescricao(e.target.value)}
          {...campos.props("descricao")}
        />
        <ErroDeCampo campo="descricao" erros={campos.erros} />
      </div>

      <label className="linha" style={{ gap: "0.4rem", marginTop: "0.8rem" }}>
        <input
          type="checkbox"
          checked={jaPago}
          onChange={(e) => setJaPago(e.target.checked)}
          style={{ width: "auto" }}
        />
        <span className="discreto">Já foi pago hoje</span>
      </label>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Salvando…" : "Registrar"}
        </button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- utilitários

function moeda(valor: number) {
  return valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}


