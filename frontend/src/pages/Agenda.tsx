import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { diaAbreviado, formatarBr, hojeIso, inicioDaSemana, somarDias } from "../api/datas";
import type {
  Agendamento,
  DiaDaAgenda,
  PacienteResumo,
  SituacaoAtendimento,
  TipoAtendimento,
  TipoAtendimentoInfo,
} from "../api/types";
import { contar, plural } from "../texto";

/** Como a situação é dita na confirmação, no mesmo vocabulário dos botões. */
const DESCRICAO_DA_SITUACAO: Record<SituacaoAtendimento, string> = {
  AGENDADO: "agendado",
  CONFIRMADO: "confirmado",
  REALIZADO: "realizado",
  FALTOU: "falta",
  CANCELADO: "cancelado",
};

const CLASSE_POR_SITUACAO: Record<SituacaoAtendimento, string> = {
  AGENDADO: "",
  CONFIRMADO: "verde",
  REALIZADO: "verde",
  FALTOU: "vermelha",
  CANCELADO: "",
};

export default function Agenda() {
  const [dia, setDia] = useState(hojeIso());
  const [visao, setVisao] = useState<"dia" | "semana">("dia");
  const [agenda, setAgenda] = useState<DiaDaAgenda | null>(null);
  const [semana, setSemana] = useState<Agendamento[]>([]);
  const [pacientes, setPacientes] = useState<PacienteResumo[]>([]);
  const [tipos, setTipos] = useState<TipoAtendimentoInfo[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [criando, setCriando] = useState(false);
  const [assinando, setAssinando] = useState(false);
  const recado = useRecado();

  const primeiroDaSemana = inicioDaSemana(dia);
  const ultimoDaSemana = somarDias(primeiroDaSemana, 6);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      if (visao === "semana") {
        setSemana(await api.agenda.naFaixa(primeiroDaSemana, ultimoDaSemana));
      } else {
        setAgenda(await api.agenda.doDia(dia));
      }
    } catch (e) {
      setErro(explicarErro(e, "abrir a agenda"));
    } finally {
      setCarregando(false);
    }
  }, [dia, visao, primeiroDaSemana, ultimoDaSemana]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  useEffect(() => {
    api.pacientes.listar({ ativo: true, size: 200 })
      .then((p) => setPacientes(p.content))
      .catch(() => setPacientes([]));
    api.agenda.tipos().then(setTipos).catch(() => setTipos([]));
  }, []);

  async function mudarSituacao(id: number, situacao: SituacaoAtendimento) {
    setErro(null);
    try {
      await api.agenda.mudarSituacao(id, situacao);
      recado.confirmar(`Atendimento marcado como ${DESCRICAO_DA_SITUACAO[situacao]}.`);
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "alterar a situação do atendimento"));
    }
  }

  function mover(dias: number) {
    setDia(somarDias(dia, dias));
  }

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Agenda</h1>
          <p>
            {visao === "semana"
              ? `${contar(semana.length, "atendimento", "atendimentos")} · ${formatarBr(primeiroDaSemana)} a ${formatarBr(ultimoDaSemana)}`
              : agenda
                ? [
                    contar(agenda.totalDeAtendimentos, "atendimento", "atendimentos"),
                    `${agenda.realizados} ${plural(agenda.realizados, "realizado", "realizados")}`,
                    ...(agenda.faltas > 0
                      ? [contar(agenda.faltas, "falta", "faltas")]
                      : []),
                  ].join(" · ")
                : "—"}
          </p>
        </div>
        <div className="linha">
          <button
            className="botao secundario"
            onClick={() => setAssinando((v) => !v)}
            aria-expanded={assinando}
          >
            Ver no meu calendário
          </button>
          <button className="botao" onClick={() => setCriando((v) => !v)}>
            {criando ? "Cancelar" : "Novo atendimento"}
          </button>
        </div>
      </div>

      {assinando && <AssinaturaDaAgenda aoFechar={() => setAssinando(false)} />}

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      {criando && (
        <FormularioAgendamento
          pacientes={pacientes}
          tipos={tipos}
          diaSugerido={dia}
          aoSalvar={async () => {
            setCriando(false);
            await carregar();
          }}
        />
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="seletor-visao" role="group" aria-label="Visão da agenda">
            <button
              type="button"
              aria-pressed={visao === "dia"}
              onClick={() => setVisao("dia")}
            >
              Dia
            </button>
            <button
              type="button"
              aria-pressed={visao === "semana"}
              onClick={() => setVisao("semana")}
            >
              Semana
            </button>
          </div>
          <button className="botao secundario pequeno" onClick={() => mover(visao === "semana" ? -7 : -1)}>
            {visao === "semana" ? "← Semana anterior" : "← Dia anterior"}
          </button>
          <div className="campo" style={{ width: 170 }}>
            <input
              type="date"
              value={dia}
              onChange={(e) => setDia(e.target.value)}
              aria-label="Data da agenda"
            />
          </div>
          <button className="botao secundario pequeno" onClick={() => mover(visao === "semana" ? 7 : 1)}>
            {visao === "semana" ? "Próxima semana →" : "Próximo dia →"}
          </button>
          <button
            className="botao secundario pequeno"
            onClick={() => setDia(hojeIso())}
          >
            Hoje
          </button>
          <span className="discreto">
            {visao === "semana"
              ? `${formatarBr(primeiroDaSemana)} — ${formatarBr(ultimoDaSemana)}`
              : diaDaSemana(dia)}
          </span>
        </div>
      </div>

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : visao === "semana" ? (
        <SemanaDaAgenda
          inicio={primeiroDaSemana}
          atendimentos={semana}
          aoEscolherDia={(d) => {
            setDia(d);
            setVisao("dia");
          }}
          aoMudarSituacao={mudarSituacao}
        />
      ) : !agenda || agenda.atendimentos.length === 0 ? (
        <div className="cartao vazio">
          Nenhum atendimento neste dia. Use <strong>Novo atendimento</strong> para marcar o
          primeiro.
        </div>
      ) : (
        /* A mesma régua do plano do paciente: o dia é uma linha, e o vão entre
           dois atendimentos aparece como vão de verdade. */
        <div className="regua-dia">
          {agenda.atendimentos.map((a) => (
            <LinhaDoAtendimento key={a.id} atendimento={a} aoMudarSituacao={mudarSituacao} />
          ))}
        </div>
      )}
    </>
  );
}

/**
 * Assinatura da agenda num calendário externo.
 *
 * Não é integração com a API do Google: é um endereço que Google Agenda, Apple
 * Calendar e Outlook assinam nativamente, e passam a buscar sozinhos. Dá o que
 * o profissional quer — ver os atendimentos no calendário que já usa — sem
 * pedir a ele que autorize um aplicativo.
 *
 * O caminho de volta não existe: um evento criado no Google não vira
 * atendimento aqui. Está dito na tela, porque descobrir isso na prática custa
 * um horário perdido.
 */
function AssinaturaDaAgenda({ aoFechar }: { aoFechar: () => void }) {
  const [endereco, setEndereco] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);
  const avisos = useRecado();

  useEffect(() => {
    api.agenda
      .assinatura()
      .then((a) => setEndereco(a.token ? urlDoCalendario(a.token) : null))
      .catch((e) => setErro(explicarErro(e, "ler a assinatura da agenda")))
      .finally(() => setCarregando(false));
  }, []);

  function urlDoCalendario(token: string) {
    return `${window.location.origin}/api/publico/agenda/${token}.ics`;
  }

  async function gerar() {
    setErro(null);
    setCopiado(false);
    try {
      const { token } = await api.agenda.gerarAssinatura();
      setEndereco(urlDoCalendario(token));
    } catch (e) {
      setErro(explicarErro(e, "gerar o endereço da assinatura"));
    }
  }

  async function desligar() {
    if (!confirm("Desligar a assinatura? O calendário que já a usa para de receber a agenda.")) {
      return;
    }
    setErro(null);
    try {
      await api.agenda.revogarAssinatura();
      setEndereco(null);
      avisos.confirmar("Assinatura desligada. O calendário para de receber a agenda.");
    } catch (e) {
      setErro(explicarErro(e, "desligar a assinatura"));
    }
  }

  async function copiar() {
    if (!endereco) return;
    try {
      await navigator.clipboard.writeText(endereco);
      setCopiado(true);
    } catch {
      // Sem permissão de área de transferência: o endereço está na tela e
      // pode ser copiado à mão, então isto não vira erro.
      setCopiado(false);
    }
  }

  return (
    <div className="cartao" style={{ marginBottom: "0.9rem" }}>
      <div className="linha" style={{ justifyContent: "space-between" }}>
        <h2>Ver a agenda no meu calendário</h2>
        <button type="button" className="botao secundario pequeno" onClick={aoFechar}>
          Fechar
        </button>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ margin: "0.6rem 0" }}>
          {erro}
        </div>
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : !endereco ? (
        <>
          <p className="minusculo" style={{ marginTop: "0.5rem" }}>
            Gere um endereço e assine-o no Google Agenda, no Apple Calendar ou no Outlook. Eles
            passam a buscar a agenda sozinhos, e o que você marcar aqui aparece lá.
          </p>
          <div className="linha" style={{ marginTop: "0.7rem" }}>
            <button type="button" className="botao" onClick={gerar}>
              Gerar endereço
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="campo" style={{ marginTop: "0.6rem" }}>
            <label htmlFor="endereco-da-agenda">Endereço da assinatura</label>
            <div className="linha" style={{ gap: "0.4rem" }}>
              <input
                id="endereco-da-agenda"
                readOnly
                value={endereco}
                onFocus={(e) => e.currentTarget.select()}
                style={{ flex: 1, fontFamily: "inherit" }}
              />
              <button type="button" className="botao secundario pequeno" onClick={copiar}>
                {copiado ? "Copiado" : "Copiar"}
              </button>
            </div>
            <span className="minusculo">
              No Google Agenda: <strong>Outros calendários → Do URL</strong>. No Apple Calendar:{" "}
              <strong>Arquivo → Nova assinatura de calendário</strong>.
            </span>
          </div>

          <p className="minusculo" style={{ marginTop: "0.7rem" }}>
            Quem tem este endereço vê a agenda com nome de paciente, sem senha — trate-o como uma
            chave. Gerar outro invalida este na hora.
          </p>
          <p className="minusculo" style={{ marginTop: "0.3rem" }}>
            O caminho é de mão única: um evento criado no seu calendário não vira atendimento aqui.
          </p>

          <div className="linha" style={{ marginTop: "0.7rem" }}>
            <button type="button" className="botao secundario pequeno" onClick={gerar}>
              Gerar outro endereço
            </button>
            <button type="button" className="botao perigo pequeno" onClick={desligar}>
              Desligar
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * Um atendimento pendurado na régua do dia.
 *
 * Extraído para que a visão de semana pudesse reaproveitá-lo: os sete dias
 * usam a mesma linha, e não uma versão reduzida que divergiria com o tempo.
 */
function LinhaDoAtendimento({
  atendimento: a,
  aoMudarSituacao,
}: {
  atendimento: Agendamento;
  aoMudarSituacao: (id: number, situacao: SituacaoAtendimento) => void;
}) {
  return (
    <div className="regua-item">
      <time className="regua-hora" dateTime={a.inicio}>
        {hora(a.inicio)}
      </time>
      <div className="regua-corpo">
        <div className="atendimento">
          <div className="atendimento-quem">
            <strong>{a.pacienteNome ?? "Sem paciente"}</strong>
            <span className={`etiqueta ${CLASSE_POR_SITUACAO[a.situacao]}`}>
              {a.situacaoDescricao}
            </span>
          </div>
          <p className="atendimento-meta">
            {a.tipoDescricao} · {hora(a.inicio)}–{hora(a.fim)} · {a.duracaoMinutos} min
          </p>
          {a.observacao && <p className="atendimento-nota">{a.observacao}</p>}
          {a.motivoDesfecho && <p className="atendimento-nota">{a.motivoDesfecho}</p>}
          <Transicoes atendimento={a} aoEscolher={aoMudarSituacao} />
        </div>
      </div>
    </div>
  );
}

/**
 * A semana como sete dias empilhados, cada um com a sua régua.
 *
 * Sete colunas lado a lado caberiam num monitor e em nenhum celular, e
 * espremeriam justamente o que interessa ler: o nome do paciente e a situação.
 * Empilhado, o dia vazio também aparece — e um buraco na agenda é informação.
 */
function SemanaDaAgenda({
  inicio,
  atendimentos,
  aoEscolherDia,
  aoMudarSituacao,
}: {
  inicio: string;
  atendimentos: Agendamento[];
  aoEscolherDia: (dia: string) => void;
  aoMudarSituacao: (id: number, situacao: SituacaoAtendimento) => void;
}) {
  const dias = Array.from({ length: 7 }, (_, i) => somarDias(inicio, i));
  const hoje = hojeIso();

  if (atendimentos.length === 0) {
    return (
      <div className="cartao vazio">
        Nenhum atendimento entre {formatarBr(inicio)} e {formatarBr(somarDias(inicio, 6))}.
      </div>
    );
  }

  return (
    <div className="semana">
      {dias.map((d) => {
        const doDia = atendimentos.filter((a) => a.inicio.slice(0, 10) === d);
        return (
          <section className={`dia-da-semana ${d === hoje ? "hoje" : ""}`} key={d}>
            <header>
              <button type="button" onClick={() => aoEscolherDia(d)}>
                <span className="rotulo-dia">{diaAbreviado(d)}</span>
                <span className="data-dia">{formatarBr(d).slice(0, 5)}</span>
              </button>
              <span className="minusculo">
                {doDia.length === 0 ? "livre" : contar(doDia.length, "atendimento", "atendimentos")}
              </span>
            </header>
            {doDia.length > 0 && (
              <div className="regua-dia">
                {doDia.map((a) => (
                  <LinhaDoAtendimento key={a.id} atendimento={a} aoMudarSituacao={aoMudarSituacao} />
                ))}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

/**
 * Só as transições que o servidor aceita a partir da situação atual.
 * Oferecer um botão que resultaria em erro seria pedir para o profissional
 * descobrir a regra por tentativa.
 */
function Transicoes({
  atendimento,
  aoEscolher,
}: {
  atendimento: Agendamento;
  aoEscolher: (id: number, situacao: SituacaoAtendimento) => void;
}) {
  if (atendimento.transicoesPermitidas.length === 0) {
    return null;
  }
  return (
    <div className="linha atendimento-acoes" style={{ gap: "0.3rem" }}>
      {atendimento.transicoesPermitidas.map((situacao) => (
        <button
          key={situacao}
          className={`botao ${situacao === "REALIZADO" ? "" : "secundario"} pequeno`}
          onClick={() => aoEscolher(atendimento.id, situacao)}
        >
          {rotuloDaSituacao(situacao)}
        </button>
      ))}
    </div>
  );
}

function FormularioAgendamento({
  pacientes,
  tipos,
  diaSugerido,
  aoSalvar,
}: {
  pacientes: PacienteResumo[];
  tipos: TipoAtendimentoInfo[];
  diaSugerido: string;
  aoSalvar: () => Promise<void>;
}) {
  const [pacienteId, setPacienteId] = useState("");
  const [data, setData] = useState(diaSugerido);
  const [horario, setHorario] = useState("09:00");
  const [tipo, setTipo] = useState<TipoAtendimento>("RETORNO");
  const [duracao, setDuracao] = useState("30");
  const [observacao, setObservacao] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  /** Ao trocar o tipo, a duração acompanha a sugestão — mas segue editável. */
  function escolherTipo(novo: TipoAtendimento) {
    setTipo(novo);
    const info = tipos.find((t) => t.tipo === novo);
    if (info) setDuracao(String(info.duracaoSugeridaMinutos));
  }

  const campos = useErrosDeCampo();
  const recado = useRecado();

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    campos.limpar();
    setEnviando(true);
    try {
      await api.agenda.agendar({
        pacienteId: Number(pacienteId),
        inicio: `${data}T${horario}:00`,
        duracaoMinutos: Number(duracao),
        tipo,
        observacao: observacao.trim() || undefined,
      });
      const quem = pacientes.find((p) => String(p.id) === pacienteId)?.nome ?? "Atendimento";
      recado.confirmar(`${quem} marcado para ${formatarBr(data)}, às ${horario}.`);
      await aoSalvar();
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "marcar o atendimento"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Novo atendimento</h2>

      {erro && <div className="aviso erro" style={{ margin: "0.8rem 0" }}>{erro}</div>}

      <div className="grade tres" style={{ marginTop: "0.8rem" }}>
        <div className="campo" style={{ gridColumn: "span 2" }}>
          <label htmlFor="ag-paciente">Paciente</label>
          <select
            id="ag-paciente"
            name="pacienteId"
            value={pacienteId}
            onChange={(e) => setPacienteId(e.target.value)}
            required
            {...campos.props("pacienteId")}
          >
            <option value="">Selecione…</option>
            {pacientes.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nome}
              </option>
            ))}
          </select>
          <ErroDeCampo campo="pacienteId" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ag-tipo">Tipo</label>
          <select
            id="ag-tipo"
            value={tipo}
            onChange={(e) => escolherTipo(e.target.value as TipoAtendimento)}
          >
            {tipos.map((t) => (
              <option key={t.tipo} value={t.tipo}>
                {t.descricao}
              </option>
            ))}
          </select>
        </div>
        <div className="campo">
          <label htmlFor="ag-data">Data</label>
          <input
            id="ag-data"
            name="inicio"
            type="date"
            value={data}
            onChange={(e) => setData(e.target.value)}
            required
            {...campos.props("inicio")}
          />
          <ErroDeCampo campo="inicio" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ag-hora">Início</label>
          <input
            id="ag-hora"
            type="time"
            value={horario}
            onChange={(e) => setHorario(e.target.value)}
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="ag-duracao">Duração (min)</label>
          <input
            id="ag-duracao"
            name="duracaoMinutos"
            inputMode="numeric"
            value={duracao}
            onChange={(e) => setDuracao(e.target.value)}
            required
            {...campos.props("duracaoMinutos")}
          />
          <ErroDeCampo campo="duracaoMinutos" erros={campos.erros} />
        </div>
      </div>

      <div className="campo" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="ag-obs">Observação</label>
        <input
          id="ag-obs"
          name="observacao"
          value={observacao}
          onChange={(e) => setObservacao(e.target.value)}
          {...campos.props("observacao")}
        />
        <ErroDeCampo campo="observacao" erros={campos.erros} />
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Agendando…" : "Agendar"}
        </button>
      </div>
    </form>
  );
}

function rotuloDaSituacao(situacao: SituacaoAtendimento) {
  const rotulos: Record<SituacaoAtendimento, string> = {
    AGENDADO: "Reabrir",
    CONFIRMADO: "Confirmar",
    REALIZADO: "Realizado",
    FALTOU: "Faltou",
    CANCELADO: "Cancelar",
  };
  return rotulos[situacao];
}

function hora(iso: string) {
  return iso.slice(11, 16);
}

function diaDaSemana(iso: string) {
  const data = new Date(`${iso}T12:00:00`);
  return data.toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" });
}
