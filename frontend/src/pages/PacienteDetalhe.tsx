import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ErroApi, api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { formatarBr } from "../api/datas";
import type {
  Paciente,
  PlanoResumo,
  Questionario,
  RespostaDeQuestionario,
} from "../api/types";

export default function PacienteDetalhe() {
  const { id } = useParams();
  const navegar = useNavigate();
  const pacienteId = Number(id);

  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [planos, setPlanos] = useState<PlanoResumo[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [editando, setEditando] = useState(false);
  const [salvando, setSalvando] = useState(false);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const [dados, listaDePlanos] = await Promise.all([
        api.pacientes.buscar(pacienteId),
        api.prescricoes.listar({ pacienteId, size: 50 }),
      ]);
      setPaciente(dados);
      setPlanos(listaDePlanos.content);
    } catch (e) {
      setErro(explicarErro(e, "abrir o paciente"));
    } finally {
      setCarregando(false);
    }
  }, [pacienteId]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function alternarSituacao() {
    if (!paciente) return;
    setSalvando(true);
    try {
      if (paciente.ativo) {
        await api.pacientes.inativar(paciente.id);
      } else {
        await api.pacientes.reativar(paciente.id);
      }
      await carregar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Falha ao alterar a situação.");
    } finally {
      setSalvando(false);
    }
  }

  if (carregando) return <p className="carregando">Carregando…</p>;
  if (erro && !paciente) return <div className="aviso erro">{erro}</div>;
  if (!paciente) return null;

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <Link to="/pacientes" className="minusculo">
            ← Pacientes
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>{paciente.nome}</h1>
          <p>
            {paciente.idade ? `${paciente.idade} anos` : "Idade não informada"}
            {paciente.objetivo ? ` · ${paciente.objetivo}` : ""}
          </p>
        </div>
        <div className="linha">
          {!paciente.ativo && <span className="etiqueta">inativo</span>}
          <button className="botao secundario" onClick={() => setEditando((v) => !v)}>
            {editando ? "Cancelar edição" : "Editar"}
          </button>
          <Link className="botao secundario" to={`/pacientes/${paciente.id}/antropometria`}>
            Antropometria
          </Link>
          <Link className="botao secundario" to={`/pacientes/${paciente.id}/exames`}>
            Exames
          </Link>
          <button
            className="botao"
            onClick={() => navegar(`/prescricoes/novo?pacienteId=${paciente.id}`)}
          >
            Nova prescrição
          </button>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {editando ? (
        <FormularioEdicao
          paciente={paciente}
          aoSalvar={async (dados) => {
            await api.pacientes.atualizar(paciente.id, dados);
            setEditando(false);
            await carregar();
          }}
        />
      ) : (
        <div className="cartao" style={{ marginBottom: "1.1rem" }}>
          <div className="grade tres">
            <Dado rotulo="E-mail" valor={paciente.email} />
            <Dado rotulo="Telefone" valor={paciente.telefone} />
            <Dado rotulo="Nascimento" valor={formatarData(paciente.dataNascimento)} />
            <Dado
              rotulo="Sexo"
              valor={paciente.sexo === "FEMININO" ? "Feminino" : paciente.sexo === "MASCULINO" ? "Masculino" : undefined}
            />
            <Dado rotulo="CPF" valor={paciente.cpf} />
            <Dado rotulo="Profissão" valor={paciente.profissao} />
          </div>
          {paciente.observacoes && (
            <div style={{ marginTop: "0.9rem" }}>
              <span className="minusculo">Observações</span>
              <p style={{ margin: "0.2rem 0 0", fontSize: "0.9rem" }}>{paciente.observacoes}</p>
            </div>
          )}
          <div className="linha" style={{ marginTop: "1rem" }}>
            <button className="botao secundario pequeno" onClick={alternarSituacao} disabled={salvando}>
              {paciente.ativo ? "Inativar paciente" : "Reativar paciente"}
            </button>
            <span className="minusculo">
              Inativar preserva todo o histórico e prescrições.
            </span>
          </div>
        </div>
      )}

      <SecaoDeQuestionarios pacienteId={paciente.id} />

      <h2 style={{ marginBottom: "0.6rem" }}>Prescrições</h2>
      {planos.length === 0 ? (
        <div className="cartao vazio">
          Nenhuma prescrição para este paciente. Crie um plano para enviar o link a ele.
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Plano</th>
                <th>Método</th>
                <th>Situação</th>
                <th className="num">Refeições</th>
                <th className="num">kcal</th>
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
                  </td>
                  <td className="discreto">{plano.metodo.toLowerCase()}</td>
                  <td>
                    <EtiquetaStatus status={plano.status} />
                  </td>
                  <td className="num">{plano.refeicoes}</td>
                  <td className="num">
                    {plano.energiaKcal ? Math.round(plano.energiaKcal) : "—"}
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

export function EtiquetaStatus({ status }: { status: string }) {
  const classe = status === "ATIVO" ? "verde" : status === "ENCERRADO" ? "" : "ambar";
  const texto = status === "ATIVO" ? "publicado" : status === "ENCERRADO" ? "encerrado" : "rascunho";
  return <span className={`etiqueta ${classe}`}>{texto}</span>;
}


/**
 * Questionários enviados a este paciente.
 *
 * Enviar antes da consulta é o que faz o atendimento começar da análise, e não
 * da coleta. O paciente responde por link, sem conta — o mesmo mecanismo pelo
 * qual ele lê o plano.
 */
function SecaoDeQuestionarios({ pacienteId }: { pacienteId: number }) {
  const [envios, setEnvios] = useState<RespostaDeQuestionario[]>([]);
  const [modelos, setModelos] = useState<Questionario[]>([]);
  const [escolhido, setEscolhido] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [copiado, setCopiado] = useState<number | null>(null);

  const carregar = useCallback(async () => {
    try {
      setEnvios(await api.questionarios.doPaciente(pacienteId));
    } catch {
      setEnvios([]);
    }
  }, [pacienteId]);

  useEffect(() => {
    void carregar();
    api.questionarios.listar().then(setModelos).catch(() => setModelos([]));
  }, [carregar]);

  async function enviar() {
    if (!escolhido) return;
    setErro(null);
    try {
      await api.questionarios.enviar(pacienteId, { questionarioId: Number(escolhido) });
      setEscolhido("");
      await carregar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Falha ao enviar.");
    }
  }

  async function copiar(envio: RespostaDeQuestionario) {
    const endereco = `${window.location.origin}/formulario/${envio.identificadorPublico}`;
    try {
      await navigator.clipboard.writeText(endereco);
      setCopiado(envio.id);
      setTimeout(() => setCopiado(null), 2000);
    } catch {
      setCopiado(null);
    }
  }

  async function cancelar(envio: RespostaDeQuestionario) {
    if (!confirm("Cancelar este envio? O link deixa de funcionar.")) return;
    try {
      await api.questionarios.cancelarEnvio(envio.id);
      await carregar();
    } catch (e) {
      setErro(e instanceof ErroApi ? e.message : "Falha ao cancelar.");
    }
  }

  return (
    <>
      <h2 style={{ marginBottom: "0.6rem" }}>Questionários</h2>

      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.6rem" }}>
          {erro}
        </div>
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="campo" style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="qz-modelo">Enviar questionário</label>
            <select
              id="qz-modelo"
              value={escolhido}
              onChange={(e) => setEscolhido(e.target.value)}
            >
              <option value="">Escolher…</option>
              {modelos.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.nome}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            className="botao"
            onClick={enviar}
            disabled={!escolhido}
            style={{ marginTop: "1.1rem" }}
          >
            Gerar link
          </button>
        </div>

        {envios.length === 0 ? (
          <p className="minusculo" style={{ marginTop: "0.7rem", marginBottom: 0 }}>
            Nenhum enviado. O paciente responde pelo link, sem precisar de conta.
          </p>
        ) : (
          envios.map((e) => (
            <div key={e.id} style={{ marginTop: "0.8rem" }}>
              <div className="linha" style={{ gap: "0.5rem" }}>
                <strong style={{ fontSize: "0.92rem" }}>{e.questionario}</strong>
                <span className={`etiqueta ${e.pendente ? "ambar" : "verde"}`}>
                  {e.pendente ? "aguardando resposta" : "respondido"}
                </span>
                {e.classificacao && <span className="etiqueta">{e.classificacao}</span>}
                {e.escore !== undefined && (
                  <span className="minusculo">escore {e.escore}</span>
                )}
                <span style={{ flex: 1 }} />
                {e.pendente ? (
                  <>
                    <button className="botao secundario pequeno" onClick={() => copiar(e)}>
                      {copiado === e.id ? "Copiado!" : "Copiar link"}
                    </button>
                    <button className="botao perigo pequeno" onClick={() => cancelar(e)}>
                      Cancelar
                    </button>
                  </>
                ) : (
                  <span className="minusculo">
                    respondido em {formatarBr(e.respondidoEm?.slice(0, 10))}
                  </span>
                )}
              </div>

              {!e.pendente && (
                <details style={{ marginTop: "0.4rem" }}>
                  <summary className="minusculo" style={{ cursor: "pointer" }}>
                    Ver respostas
                  </summary>
                  {e.itens.map((i, n) => (
                    <div key={n} className="orientacao-anexada" style={{ marginTop: "0.6rem" }}>
                      <h3>{i.pergunta}</h3>
                      <p>{i.valor}</p>
                    </div>
                  ))}
                </details>
              )}
            </div>
          ))
        )}
      </div>
    </>
  );
}

function Dado({ rotulo, valor }: { rotulo: string; valor?: string }) {
  return (
    <div>
      <span className="minusculo">{rotulo}</span>
      <div style={{ fontSize: "0.92rem" }}>{valor || "—"}</div>
    </div>
  );
}

function formatarData(iso?: string) {
  if (!iso) return undefined;
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

function FormularioEdicao({
  paciente,
  aoSalvar,
}: {
  paciente: Paciente;
  aoSalvar: (dados: Partial<Paciente>) => Promise<void>;
}) {
  const [dados, setDados] = useState({
    nome: paciente.nome,
    email: paciente.email ?? "",
    telefone: paciente.telefone ?? "",
    dataNascimento: paciente.dataNascimento ?? "",
    sexo: paciente.sexo ?? "",
    cpf: paciente.cpf ?? "",
    profissao: paciente.profissao ?? "",
    objetivo: paciente.objetivo ?? "",
    observacoes: paciente.observacoes ?? "",
  });
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const campos = useErrosDeCampo();
  const recado = useRecado();

  function alterar(campo: keyof typeof dados, valor: string) {
    setDados((atual) => ({ ...atual, [campo]: valor }));
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await aoSalvar({
        ...dados,
        email: dados.email || undefined,
        telefone: dados.telefone || undefined,
        dataNascimento: dados.dataNascimento || undefined,
        sexo: (dados.sexo || undefined) as never,
        cpf: dados.cpf || undefined,
        profissao: dados.profissao || undefined,
        objetivo: dados.objetivo || undefined,
        observacoes: dados.observacoes || undefined,
      });
      recado.confirmar("Ficha atualizada.");
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "salvar a ficha do paciente"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "1.1rem" }} onSubmit={enviar}>
      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.85rem" }}>
          {erro}
        </div>
      )}
      <div className="grade duas">
        <div className="campo">
          <label htmlFor="ed-nome">Nome</label>
          <input id="ed-nome" value={dados.nome} onChange={(e) => alterar("nome", e.target.value)} required name="nome" {...campos.props("nome")} />
          <ErroDeCampo campo="nome" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ed-email">E-mail</label>
          <input id="ed-email" type="email" value={dados.email} onChange={(e) => alterar("email", e.target.value)} name="email" {...campos.props("email")} />
          <ErroDeCampo campo="email" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ed-tel">Telefone</label>
          <input id="ed-tel" value={dados.telefone} onChange={(e) => alterar("telefone", e.target.value)} name="telefone" {...campos.props("telefone")} />
          <ErroDeCampo campo="telefone" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ed-nasc">Nascimento</label>
          <input
            id="ed-nasc"
            type="date"
            value={dados.dataNascimento}
            max={new Date().toISOString().slice(0, 10)}
            onChange={(e) => alterar("dataNascimento", e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="ed-sexo">Sexo</label>
          <select id="ed-sexo" value={dados.sexo} onChange={(e) => alterar("sexo", e.target.value)}>
            <option value="">Não informado</option>
            <option value="FEMININO">Feminino</option>
            <option value="MASCULINO">Masculino</option>
          </select>
        </div>
        <div className="campo">
          <label htmlFor="ed-cpf">CPF</label>
          <input id="ed-cpf" value={dados.cpf} onChange={(e) => alterar("cpf", e.target.value)} name="cpf" {...campos.props("cpf")} />
          <ErroDeCampo campo="cpf" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ed-prof">Profissão</label>
          <input id="ed-prof" value={dados.profissao} onChange={(e) => alterar("profissao", e.target.value)} name="profissao" {...campos.props("profissao")} />
          <ErroDeCampo campo="profissao" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="ed-obj">Objetivo</label>
          <input id="ed-obj" value={dados.objetivo} onChange={(e) => alterar("objetivo", e.target.value)} name="objetivo" {...campos.props("objetivo")} />
          <ErroDeCampo campo="objetivo" erros={campos.erros} />
        </div>
      </div>
      <div className="campo" style={{ marginTop: "0.85rem" }}>
        <label htmlFor="ed-obs">Observações</label>
        <textarea
          id="ed-obs"
          rows={3}
          value={dados.observacoes}
          onChange={(e) => alterar("observacoes", e.target.value)}
        />
      </div>
      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Salvando…" : "Salvar alterações"}
        </button>
      </div>
    </form>
  );
}
