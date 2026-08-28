import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { formatarBr, hojeIso } from "../api/datas";
import type {
  Avaliacao,
  Gestacao,
  Derivado,
  Evolucao,
  ProtocoloComposicao,
  ProtocoloInfo,
  Variacao,
} from "../api/types";
import { contar } from "../texto";

const DOBRAS: { chave: string; rotulo: string }[] = [
  { chave: "TRICIPITAL", rotulo: "Tricipital" },
  { chave: "BICIPITAL", rotulo: "Bicipital" },
  { chave: "SUBESCAPULAR", rotulo: "Subescapular" },
  { chave: "SUPRAILIACA", rotulo: "Supra-ilíaca" },
  { chave: "ABDOMINAL", rotulo: "Abdominal" },
  { chave: "PEITORAL", rotulo: "Peitoral" },
  { chave: "COXA", rotulo: "Coxa" },
  { chave: "PANTURRILHA", rotulo: "Panturrilha" },
  { chave: "AXILAR_MEDIA", rotulo: "Axilar média" },
];

const CIRCUNFERENCIAS: { chave: string; rotulo: string }[] = [
  { chave: "cintura", rotulo: "Cintura" },
  { chave: "quadril", rotulo: "Quadril" },
  { chave: "abdomen", rotulo: "Abdômen" },
  { chave: "braco", rotulo: "Braço" },
  { chave: "antebraco", rotulo: "Antebraço" },
  { chave: "coxa", rotulo: "Coxa" },
  { chave: "panturrilha", rotulo: "Panturrilha" },
  { chave: "torax", rotulo: "Tórax" },
];

const CLASSIFICACOES: Record<string, string> = {
  BAIXO_PESO: "Baixo peso",
  EUTROFIA: "Eutrofia",
  SOBREPESO: "Sobrepeso",
  OBESIDADE_I: "Obesidade grau I",
  OBESIDADE_II: "Obesidade grau II",
  OBESIDADE_III: "Obesidade grau III",
};

const RISCOS: Record<string, string> = {
  BAIXO: "Risco baixo",
  MODERADO: "Risco moderado",
  ALTO: "Risco alto",
};

export default function Antropometria() {
  const { id } = useParams();
  const pacienteId = Number(id);

  const [avaliacoes, setAvaliacoes] = useState<Avaliacao[]>([]);
  const [evolucao, setEvolucao] = useState<Evolucao | null>(null);
  const [protocolos, setProtocolos] = useState<ProtocoloInfo[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [criando, setCriando] = useState(false);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const [lista, serie] = await Promise.all([
        api.antropometria.listar(pacienteId),
        api.antropometria.evolucao(pacienteId),
      ]);
      setAvaliacoes(lista);
      setEvolucao(serie);
    } catch (e) {
      setErro(explicarErro(e, "abrir as avaliações"));
    } finally {
      setCarregando(false);
    }
  }, [pacienteId]);

  useEffect(() => {
    void carregar();
    api.antropometria.protocolos().then(setProtocolos).catch(() => setProtocolos([]));
  }, [carregar]);

  const recado = useRecado();

  async function remover(avaliacaoId: number) {
    if (!confirm("Remover esta avaliação?")) return;
    try {
      await api.antropometria.remover(avaliacaoId);
      recado.confirmar("Avaliação removida.");
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "remover a avaliação"));
    }
  }

  if (carregando) return <p className="carregando">Carregando…</p>;

  const maisRecente = avaliacoes[avaliacoes.length - 1];

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <Link to={`/pacientes/${pacienteId}`} className="minusculo">
            ← {evolucao?.pacienteNome ?? "Paciente"}
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>Antropometria</h1>
          <p>
            {avaliacoes.length === 0
              ? "Nenhuma avaliação registrada."
              : `${avaliacoes.length} ${avaliacoes.length === 1 ? "avaliação" : "avaliações"}`}
          </p>
        </div>
        <button className="botao" onClick={() => setCriando((v) => !v)}>
          {criando ? "Cancelar" : "Nova avaliação"}
        </button>
      </div>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      {criando && (
        <FormularioAvaliacao
          pacienteId={pacienteId}
          protocolos={protocolos}
          ultima={maisRecente}
          aoSalvar={async () => {
            setCriando(false);
            await carregar();
          }}
        />
      )}

      {maisRecente && <ResumoDaAvaliacao avaliacao={maisRecente} pacienteId={pacienteId} />}

      {evolucao && evolucao.pontos.length > 1 && <TabelaDeEvolucao evolucao={evolucao} />}

      {avaliacoes.length > 0 && (
        <>
          <h2 style={{ margin: "1.3rem 0 0.6rem" }}>Histórico</h2>
          <div className="rolagem">
            <table>
              <thead>
                <tr>
                  <th>Data</th>
                  <th className="num">Peso</th>
                  <th className="num">IMC</th>
                  <th>Classificação</th>
                  <th className="num">% gordura</th>
                  <th>Protocolo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {[...avaliacoes].reverse().map((a) => (
                  <tr key={a.id}>
                    <td className="mono">{formatarBr(a.data)}</td>
                    <td className="num">{a.pesoKg ? `${num(a.pesoKg)} kg` : "—"}</td>
                    <td className="num">{a.imc ? num(a.imc) : "—"}</td>
                    <td>
                      {a.classificacaoImc.valor ? (
                        CLASSIFICACOES[a.classificacaoImc.valor]
                      ) : (
                        <span className="minusculo">não classificado</span>
                      )}
                    </td>
                    <td className="num">
                      {a.composicao?.percentualGordura
                        ? `${num(a.composicao.percentualGordura)}%`
                        : "—"}
                    </td>
                    <td className="discreto">{a.composicao?.protocoloDescricao ?? "—"}</td>
                    <td style={{ textAlign: "right" }}>
                      <button className="botao perigo pequeno" onClick={() => remover(a.id)}>
                        Remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

// ------------------------------------------------------------------ resumo

function ResumoDaAvaliacao({
  avaliacao,
  pacienteId,
}: {
  avaliacao: Avaliacao;
  pacienteId: number;
}) {
  return (
    <div className="cartao" style={{ marginBottom: "1.1rem" }}>
      <div className="linha" style={{ justifyContent: "space-between" }}>
        <h2>Última avaliação</h2>
        <span className="minusculo mono">{formatarBr(avaliacao.data)}</span>
      </div>

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <Indicador rotulo="Peso" valor={avaliacao.pesoKg} unidade="kg" />
        <Indicador rotulo="Altura" valor={avaliacao.alturaCm} unidade="cm" />
        <Indicador rotulo="IMC" valor={avaliacao.imc} />
        <Derivada rotulo="Classificação" derivado={avaliacao.classificacaoImc} mapa={CLASSIFICACOES} />
        <Indicador rotulo="Cintura/quadril" valor={avaliacao.relacaoCinturaQuadril} />
        <Derivada
          rotulo="Risco cardiometabólico"
          derivado={avaliacao.riscoCardiometabolico}
          mapa={RISCOS}
        />
      </div>

      {avaliacao.composicao && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Composição corporal
            <span className="etiqueta" style={{ marginLeft: "0.5rem" }}>
              {avaliacao.composicao.protocoloDescricao}
            </span>
          </h3>
          <div className="grade tres">
            <Indicador
              rotulo="Gordura corporal"
              valor={avaliacao.composicao.percentualGordura}
              unidade="%"
            />
            <Indicador rotulo="Massa gorda" valor={avaliacao.composicao.massaGordaKg} unidade="kg" />
            <Indicador rotulo="Massa magra" valor={avaliacao.composicao.massaMagraKg} unidade="kg" />
          </div>
        </>
      )}

      {avaliacao.crescimentoInfantil?.valor && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Crescimento
            <span className="etiqueta" style={{ marginLeft: "0.5rem" }}>
              {avaliacao.crescimentoInfantil.valor.idadeEmMeses} meses
            </span>
          </h3>
          {avaliacao.crescimentoInfantil.valor.indicadores.map((i) => (
            <div className="nutriente-linha" key={i.indicador}>
              <span>
                {i.indicadorDescricao}
                <div className="minusculo">{i.referencia}</div>
              </span>
              <span style={{ textAlign: "right" }}>
                escore-z {i.escoreZ?.toFixed(2).replace(".", ",")}
                <div>
                  <span className={`etiqueta ${i.exigeAtencao ? "ambar" : "verde"}`}>
                    {i.classificacaoDescricao}
                  </span>
                </div>
              </span>
            </div>
          ))}
        </>
      )}

      {avaliacao.gestacao?.valor && <BlocoGestacao gestacao={avaliacao.gestacao.valor} />}

      {avaliacao.gestacao && !avaliacao.gestacao.valor && (
        <div className="aviso atencao" style={{ marginTop: "0.9rem" }}>
          {avaliacao.gestacao.indisponivelPorque}
        </div>
      )}

      {avaliacao.gastoEnergetico && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>
            Gasto energético
            <span className="etiqueta" style={{ marginLeft: "0.5rem" }}>
              {avaliacao.gastoEnergetico.equacaoDescricao}
            </span>
          </h3>
          <div className="grade tres">
            <Indicador rotulo="Basal" valor={avaliacao.gastoEnergetico.basalKcal} unidade="kcal" />
            <Indicador
              rotulo="Fator de atividade"
              valor={avaliacao.gastoEnergetico.fatorAtividade}
            />
            <Indicador rotulo="Total" valor={avaliacao.gastoEnergetico.totalKcal} unidade="kcal" />
          </div>
          {avaliacao.gastoEnergetico.totalKcal !== undefined && (
            <div className="linha" style={{ marginTop: "0.7rem" }}>
              <Link
                className="botao secundario pequeno"
                to={`/prescricoes/novo?pacienteId=${pacienteId}&meta=${avaliacao.gastoEnergetico.totalKcal}`}
              >
                Usar como meta do plano
              </Link>
              <span className="minusculo">
                Abre um plano novo com a meta energética já preenchida.
              </span>
            </div>
          )}
        </>
      )}

      {Object.keys(avaliacao.circunferencias).length > 0 && (
        <>
          <h3 style={{ margin: "1rem 0 0.4rem" }}>Circunferências (cm)</h3>
          <div className="grade tres">
            {CIRCUNFERENCIAS.filter((c) => avaliacao.circunferencias[c.chave] !== undefined).map(
              (c) => (
                <Indicador
                  key={c.chave}
                  rotulo={c.rotulo}
                  valor={avaliacao.circunferencias[c.chave]}
                  unidade="cm"
                />
              ),
            )}
          </div>
        </>
      )}

      {avaliacao.observacoes && (
        <p className="discreto" style={{ marginTop: "0.9rem", marginBottom: 0 }}>
          {avaliacao.observacoes}
        </p>
      )}
    </div>
  );
}

/**
 * Ganho de peso na gestação.
 *
 * A faixa esperada vem do IMC anterior à gestação, e não do atual: o IMC de
 * hoje já embute o ganho que se quer avaliar.
 */
function BlocoGestacao({ gestacao }: { gestacao: Gestacao }) {
  const classe =
    gestacao.situacao === "ADEQUADO" ? "verde" : gestacao.situacao === "ACIMA" ? "vermelha" : "ambar";

  return (
    <>
      <h3 style={{ margin: "1rem 0 0.4rem" }}>
        Gestação
        <span className="etiqueta" style={{ marginLeft: "0.5rem" }}>
          {gestacao.semanaGestacional}ª semana
        </span>
      </h3>

      <div className="grade tres">
        <Indicador rotulo="IMC pré-gestacional" valor={gestacao.imcPreGestacional} />
        <Indicador rotulo="Ganho até agora" valor={gestacao.ganhoAteAgora} unidade="kg" />
        <div>
          <span className="minusculo">Situação</span>
          <div style={{ marginTop: "0.2rem" }}>
            <span className={`etiqueta ${classe}`}>{gestacao.situacaoDescricao}</span>
          </div>
        </div>
      </div>

      <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
        Faixa {gestacao.faixaDescricao.toLowerCase()}: esperado{" "}
        {gestacao.esperadoMin.toLocaleString("pt-BR")} a{" "}
        {gestacao.esperadoMax.toLocaleString("pt-BR")} kg até a {gestacao.semanaGestacional}ª
        semana, e {gestacao.ganhoTotalRecomendadoMin.toLocaleString("pt-BR")} a{" "}
        {gestacao.ganhoTotalRecomendadoMax.toLocaleString("pt-BR")} kg na gestação inteira
        (IOM, 2009).
      </p>
    </>
  );
}

function Indicador({
  rotulo,
  valor,
  unidade,
}: {
  rotulo: string;
  valor?: number;
  unidade?: string;
}) {
  return (
    <div>
      <span className="minusculo">{rotulo}</span>
      <div className="mono" style={{ fontSize: "1.05rem", fontWeight: 600 }}>
        {valor === undefined || valor === null ? (
          <span style={{ color: "var(--ink-faint)", fontWeight: 400, fontSize: "0.9rem" }}>
            não medido
          </span>
        ) : (
          `${num(valor)}${unidade ? ` ${unidade}` : ""}`
        )}
      </div>
    </div>
  );
}

/**
 * Valor derivado que pode não existir por regra, e não por falta de medida.
 * Mostrar o motivo é o que diz ao profissional o que fazer a respeito.
 */
function Derivada<T extends string>({
  rotulo,
  derivado,
  mapa,
}: {
  rotulo: string;
  derivado: Derivado<T>;
  mapa: Record<string, string>;
}) {
  return (
    <div>
      <span className="minusculo">{rotulo}</span>
      {derivado.valor ? (
        <div style={{ fontSize: "1.05rem", fontWeight: 600 }}>{mapa[derivado.valor]}</div>
      ) : (
        <div className="minusculo" style={{ marginTop: "0.15rem", lineHeight: 1.35 }}>
          {derivado.indisponivelPorque ?? "não disponível"}
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- evolução

function TabelaDeEvolucao({ evolucao }: { evolucao: Evolucao }) {
  const medidas = ["pesoKg", "imc", "circCintura", "percentualGordura"];

  return (
    <div className="cartao" style={{ marginBottom: "1.1rem" }}>
      <h2>Evolução</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.8rem" }}>
        A variação só aparece quando as duas avaliações são comparáveis: medida presente nas duas,
        e composição estimada pelo mesmo protocolo.
      </p>

      <div className="rolagem">
        <table>
          <thead>
            <tr>
              <th>Data</th>
              {medidas.map((m) => (
                <th key={m} className="num">
                  {rotuloDaMedida(evolucao, m)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {evolucao.pontos.map((ponto, indice) => (
              <tr key={ponto.avaliacaoId}>
                <td className="mono">{formatarBr(ponto.data)}</td>
                {medidas.map((medida) => {
                  const v = ponto.variacoesFrenteAAnterior.find((x) => x.medida === medida);
                  return (
                    <td key={medida} className="num">
                      <ValorComVariacao variacao={v} primeira={indice === 0} />
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ValorComVariacao({
  variacao,
  primeira,
}: {
  variacao?: Variacao;
  primeira: boolean;
}) {
  if (!variacao) return <span className="minusculo">—</span>;

  const atual = variacao.atual;
  if (atual === undefined || atual === null) {
    return <span className="minusculo">—</span>;
  }

  if (primeira) {
    return <span>{num(atual)}</span>;
  }

  if (!variacao.comparavel) {
    return (
      <span title={variacao.observacao}>
        {num(atual)}{" "}
        <span className="minusculo" style={{ cursor: "help" }}>
          (sem comparação)
        </span>
      </span>
    );
  }

  const d = variacao.diferenca ?? 0;
  const cor = d < 0 ? "var(--accent)" : d > 0 ? "var(--alerta)" : "var(--ink-faint)";
  return (
    <span>
      {num(atual)}{" "}
      <span style={{ color: cor, fontSize: "0.8rem" }}>
        {d > 0 ? "+" : ""}
        {num(d)}
      </span>
    </span>
  );
}

function rotuloDaMedida(evolucao: Evolucao, medida: string) {
  for (const ponto of evolucao.pontos) {
    const v = ponto.variacoesFrenteAAnterior.find((x) => x.medida === medida);
    if (v) return v.rotulo;
  }
  return medida;
}

// ---------------------------------------------------------------- formulário

function FormularioAvaliacao({
  pacienteId,
  protocolos,
  ultima,
  aoSalvar,
}: {
  pacienteId: number;
  protocolos: ProtocoloInfo[];
  ultima?: Avaliacao;
  aoSalvar: () => Promise<void>;
}) {
  const hoje = hojeIso();

  const [data, setData] = useState(hoje);
  // A altura raramente muda entre consultas: repetir a última poupa digitação.
  const [peso, setPeso] = useState("");
  const [altura, setAltura] = useState(ultima?.alturaCm ? String(ultima.alturaCm) : "");
  const [dobras, setDobras] = useState<Record<string, string>>({});
  const [circunferencias, setCircunferencias] = useState<Record<string, string>>({});
  const [protocolo, setProtocolo] = useState<string>("");
  const [equacao, setEquacao] = useState<string>("");
  const [fator, setFator] = useState("1.55");
  const [observacoes, setObservacoes] = useState("");
  const [gestante, setGestante] = useState(false);
  const [semana, setSemana] = useState("");
  const [pesoPre, setPesoPre] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const campos = useErrosDeCampo();
  const recado = useRecado();

  const escolhido = protocolos.find((p) => p.protocolo === protocolo);
  // Sem saber o sexo do paciente aqui, destaca a união das duas listas.
  const exigidas = new Set([
    ...(escolhido?.dobrasFemininas ?? []),
    ...(escolhido?.dobrasMasculinas ?? []),
  ]);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await api.antropometria.criar(pacienteId, {
        data,
        pesoKg: numeroOuIndefinido(peso),
        alturaCm: numeroOuIndefinido(altura),
        dobras: mapaDeNumeros(dobras),
        circunferencias: mapaDeNumeros(circunferencias),
        protocoloComposicao: (protocolo || undefined) as ProtocoloComposicao | undefined,
        equacaoGasto: (equacao || undefined) as never,
        fatorAtividade: equacao ? numeroOuIndefinido(fator) : undefined,
        observacoes: observacoes.trim() || undefined,
        semanaGestacional: gestante && semana ? Number(semana) : undefined,
        pesoPreGestacionalKg:
          gestante && pesoPre ? Number(pesoPre.replace(",", ".")) : undefined,
      });
      recado.confirmar(`Avaliação de ${formatarBr(data)} registrada.`);
      await aoSalvar();
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "registrar a avaliação"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "1.1rem" }} onSubmit={enviar}>
      <h2>Nova avaliação</h2>

      {erro && <div className="aviso erro" style={{ margin: "0.8rem 0" }}>{erro}</div>}

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <div className="campo">
          <label htmlFor="av-data">Data</label>
          <input
            id="av-data"
            name="data"
            type="date"
            value={data}
            max={hoje}
            onChange={(e) => setData(e.target.value)}
            required
            {...campos.props("data")}
          />
          <ErroDeCampo campo="data" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="av-peso">Peso (kg)</label>
          <input
            id="av-peso"
            name="pesoKg"
            inputMode="decimal"
            value={peso}
            onChange={(e) => setPeso(e.target.value)}
            {...campos.props("pesoKg")}
          />
          <ErroDeCampo campo="pesoKg" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="av-altura">Altura (cm)</label>
          <input
            id="av-altura"
            name="alturaCm"
            inputMode="decimal"
            value={altura}
            onChange={(e) => setAltura(e.target.value)}
            {...campos.props("alturaCm")}
          />
          <ErroDeCampo campo="alturaCm" erros={campos.erros} />
        </div>
      </div>

      <h3 style={{ margin: "1.1rem 0 0.4rem" }}>Circunferências (cm)</h3>
      <div className="grade tres">
        {CIRCUNFERENCIAS.map((c) => (
          <div className="campo" key={c.chave}>
            <label htmlFor={`circ-${c.chave}`}>{c.rotulo}</label>
            <input
              id={`circ-${c.chave}`}
              inputMode="decimal"
              value={circunferencias[c.chave] ?? ""}
              onChange={(e) =>
                setCircunferencias((v) => ({ ...v, [c.chave]: e.target.value }))
              }
            />
          </div>
        ))}
      </div>

      <h3 style={{ margin: "1.1rem 0 0.4rem" }}>Dobras cutâneas (mm)</h3>
      <div className="grade tres">
        {DOBRAS.map((d) => {
          const exigida = exigidas.has(d.chave);
          return (
            <div className="campo" key={d.chave}>
              <label htmlFor={`dobra-${d.chave}`}>
                {d.rotulo}
                {exigida && (
                  <span className="etiqueta verde" style={{ marginLeft: "0.35rem" }}>
                    exigida
                  </span>
                )}
              </label>
              <input
                id={`dobra-${d.chave}`}
                inputMode="decimal"
                value={dobras[d.chave] ?? ""}
                onChange={(e) => setDobras((v) => ({ ...v, [d.chave]: e.target.value }))}
              />
            </div>
          );
        })}
      </div>

      <div className="grade duas" style={{ marginTop: "1.1rem" }}>
        <div className="campo">
          <label htmlFor="av-protocolo">Estimar composição por</label>
          <select
            id="av-protocolo"
            value={protocolo}
            onChange={(e) => setProtocolo(e.target.value)}
          >
            <option value="">Não estimar</option>
            {protocolos.map((p) => (
              <option key={p.protocolo} value={p.protocolo}>
                {p.descricao}
              </option>
            ))}
          </select>
          {escolhido && (
            <span className="minusculo">
              Exige {contar(exigidas.size, "dobra", "dobras")}. Faltando alguma, o sistema recusa
              e diz qual.
            </span>
          )}
        </div>

        <div className="campo">
          <label htmlFor="av-equacao">Estimar gasto energético por</label>
          <select id="av-equacao" value={equacao} onChange={(e) => setEquacao(e.target.value)}>
            <option value="">Não estimar</option>
            <option value="MIFFLIN_ST_JEOR">Mifflin-St Jeor</option>
            <option value="HARRIS_BENEDICT">Harris-Benedict revisada</option>
          </select>
        </div>
      </div>

      {equacao && (
        <div className="campo" style={{ marginTop: "0.6rem", maxWidth: 260 }}>
          <label htmlFor="av-fator">Fator de atividade</label>
          <select
            id="av-fator"
            name="fatorAtividade"
            value={fator}
            onChange={(e) => setFator(e.target.value)}
            {...campos.props("fatorAtividade")}
          >
            <option value="1.2">1,20 — sedentário</option>
            <option value="1.375">1,375 — levemente ativo</option>
            <option value="1.55">1,55 — moderadamente ativo</option>
            <option value="1.725">1,725 — muito ativo</option>
            <option value="1.9">1,90 — extremamente ativo</option>
          </select>
          <ErroDeCampo campo="fatorAtividade" erros={campos.erros} />
        </div>
      )}

      <label className="linha" style={{ gap: "0.4rem", marginTop: "0.9rem" }}>
        <input
          type="checkbox"
          checked={gestante}
          onChange={(e) => setGestante(e.target.checked)}
          style={{ width: "auto" }}
        />
        <span className="discreto">Gestante</span>
      </label>

      {gestante && (
        <div className="grade duas" style={{ marginTop: "0.5rem" }}>
          <div className="campo">
            <label htmlFor="av-semana">Semana gestacional</label>
            <input
              id="av-semana"
              name="semanaGestacional"
              inputMode="numeric"
              value={semana}
              onChange={(e) => setSemana(e.target.value)}
              placeholder="20"
              {...campos.props("semanaGestacional")}
            />
            <ErroDeCampo campo="semanaGestacional" erros={campos.erros} />
          </div>
          <div className="campo">
            <label htmlFor="av-peso-pre">Peso antes da gestação (kg)</label>
            <input
              id="av-peso-pre"
              name="pesoPreGestacionalKg"
              inputMode="decimal"
              value={pesoPre}
              onChange={(e) => setPesoPre(e.target.value)}
              placeholder="61,2"
              {...campos.props("pesoPreGestacionalKg")}
            />
            <ErroDeCampo campo="pesoPreGestacionalKg" erros={campos.erros} />
            <span className="minusculo">
              A faixa de ganho vem do IMC de antes. O IMC de hoje já embute o ganho que se quer
              avaliar.
            </span>
          </div>
        </div>
      )}

      <div className="campo" style={{ marginTop: "0.8rem" }}>
        <label htmlFor="av-obs">Observações</label>
        <textarea
          id="av-obs"
          name="observacoes"
          rows={2}
          value={observacoes}
          onChange={(e) => setObservacoes(e.target.value)}
          {...campos.props("observacoes")}
        />
        <ErroDeCampo campo="observacoes" erros={campos.erros} />
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Salvando…" : "Registrar avaliação"}
        </button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- utilitários

function numeroOuIndefinido(texto: string): number | undefined {
  const limpo = texto.replace(",", ".").trim();
  if (!limpo) return undefined;
  const valor = Number(limpo);
  return Number.isFinite(valor) ? valor : undefined;
}

function mapaDeNumeros(origem: Record<string, string>): Record<string, number> {
  const saida: Record<string, number> = {};
  Object.entries(origem).forEach(([chave, texto]) => {
    const valor = numeroOuIndefinido(texto);
    if (valor !== undefined && valor > 0) saida[chave] = valor;
  });
  return saida;
}

function num(valor: number) {
  return valor.toFixed(2).replace(".", ",").replace(/,00$/, "");
}


