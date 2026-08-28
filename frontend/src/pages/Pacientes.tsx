import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import type { PacienteResumo, ResultadoImportacao } from "../api/types";
import { contar, plural } from "../texto";

export default function Pacientes() {
  const navegar = useNavigate();

  const [pacientes, setPacientes] = useState<PacienteResumo[]>([]);
  const [total, setTotal] = useState(0);
  const [termo, setTermo] = useState("");
  const [somenteAtivos, setSomenteAtivos] = useState(true);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [painel, setPainel] = useState<"nenhum" | "novo" | "importar">("nenhum");

  const buscar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const pagina = await api.pacientes.listar({
        termo: termo.trim() || undefined,
        ativo: somenteAtivos ? true : undefined,
        size: 50,
      });
      setPacientes(pagina.content);
      setTotal(pagina.totalElements);
    } catch (e) {
      setErro(explicarErro(e, "abrir a lista de pacientes"));
    } finally {
      setCarregando(false);
    }
  }, [termo, somenteAtivos]);

  // Busca com atraso para não disparar uma consulta por tecla digitada.
  useEffect(() => {
    const relogio = setTimeout(buscar, termo ? 300 : 0);
    return () => clearTimeout(relogio);
  }, [buscar, termo]);

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Pacientes</h1>
          <p>
            {contar(total, "paciente", "pacientes")}
            {somenteAtivos ? plural(total, " ativo", " ativos") : " no total"}
          </p>
        </div>
        <div className="linha">
          <button
            className="botao secundario"
            onClick={() => setPainel(painel === "importar" ? "nenhum" : "importar")}
          >
            Importar planilha
          </button>
          <button
            className="botao"
            onClick={() => setPainel(painel === "novo" ? "nenhum" : "novo")}
          >
            Novo paciente
          </button>
        </div>
      </div>

      {painel === "importar" && (
        <PainelImportacao aoFechar={() => setPainel("nenhum")} aoImportar={buscar} />
      )}
      {painel === "novo" && (
        <FormularioNovoPaciente
          aoFechar={() => setPainel("nenhum")}
          aoCriar={(id) => navegar(`/pacientes/${id}`)}
        />
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="linha">
          <div className="campo" style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="busca">Buscar</label>
            <input
              id="busca"
              value={termo}
              onChange={(e) => setTermo(e.target.value)}
              placeholder="Nome, e-mail ou telefone"
            />
          </div>
          <label className="linha" style={{ gap: "0.4rem", paddingTop: "1.1rem" }}>
            <input
              type="checkbox"
              checked={somenteAtivos}
              onChange={(e) => setSomenteAtivos(e.target.checked)}
              style={{ width: "auto" }}
            />
            <span className="discreto">Somente ativos</span>
          </label>
        </div>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : pacientes.length === 0 ? (
        <div className="cartao vazio">
          {termo
            ? `Nenhum paciente encontrado para "${termo}". Tente parte do nome, do e-mail ou do telefone.`
            : "Nenhum paciente ainda. Use Novo paciente para cadastrar o primeiro."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>E-mail</th>
                <th className="num">Idade</th>
                <th>Situação</th>
              </tr>
            </thead>
            <tbody>
              {pacientes.map((p) => (
                <tr
                  key={p.id}
                  className="clicavel"
                  onClick={() => navegar(`/pacientes/${p.id}`)}
                >
                  <td>
                    <strong>{p.nome}</strong>
                  </td>
                  <td className="discreto">{p.email ?? "—"}</td>
                  <td className="num">{p.idade ?? "—"}</td>
                  <td>
                    <span className={`etiqueta ${p.ativo ? "verde" : ""}`}>
                      {p.ativo ? "ativo" : "inativo"}
                    </span>
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
 * Importação de pacientes a partir de planilha.
 *
 * É o primeiro obstáculo de quem adota o sistema: quem já atende tem os
 * pacientes numa planilha ou noutro sistema, e digitar duzentos à mão não é
 * uma opção. Sem os pacientes, nada mais do produto pode ser experimentado.
 */
function PainelImportacao({
  aoFechar,
  aoImportar,
}: {
  aoFechar: () => void;
  aoImportar: () => void;
}) {
  const entrada = useRef<HTMLInputElement>(null);
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
      const saida = await api.pacientes.importar(arquivo, separador);
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
      <h2>Importar planilha de pacientes</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        Só a coluna <code>nome</code> é obrigatória. São reconhecidas também{" "}
        <code>email</code>, <code>telefone</code>, <code>nascimento</code>, <code>sexo</code>,{" "}
        <code>cpf</code>, <code>profissao</code>, <code>objetivo</code> e{" "}
        <code>observacoes</code> — sem diferenciar acento nem maiúscula. Uma linha com problema
        é anotada e as outras entram.
      </p>

      {erro && (
        <div className="aviso erro" style={{ marginBottom: "0.85rem" }}>
          {erro}
        </div>
      )}

      {resultado && (
        <div
          className={`aviso ${resultado.importados > 0 ? "ok" : "atencao"}`}
          style={{ marginBottom: "0.85rem" }}
        >
          <strong>
            {contar(resultado.importados, "paciente importado", "pacientes importados")}
            {resultado.ignorados > 0
              ? `, ${contar(resultado.ignorados, "linha ignorada", "linhas ignoradas")}`
              : ""}
            .
          </strong>
          {resultado.avisos.length > 0 && (
            <ul style={{ margin: "0.4rem 0 0", paddingLeft: "1.1rem" }}>
              {resultado.avisos.slice(0, 8).map((aviso, i) => (
                <li key={i}>{aviso}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="linha">
        <div className="campo" style={{ flex: 2, minWidth: 220 }}>
          <label htmlFor="imp-pac-arquivo">Arquivo CSV</label>
          <input id="imp-pac-arquivo" type="file" accept=".csv,text/csv" ref={entrada} />
        </div>
        <div className="campo" style={{ width: 120 }}>
          <label htmlFor="imp-pac-sep">Separador</label>
          <select
            id="imp-pac-sep"
            value={separador}
            onChange={(e) => setSeparador(e.target.value)}
          >
            <option value=",">Vírgula</option>
            <option value=";">Ponto e vírgula</option>
            <option value={"	"}>Tabulação</option>
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

function FormularioNovoPaciente({
  aoFechar,
  aoCriar,
}: {
  aoFechar: () => void;
  aoCriar: (id: number) => void;
}) {
  const [nome, setNome] = useState("");
  const [email, setEmail] = useState("");
  const [telefone, setTelefone] = useState("");
  const [dataNascimento, setDataNascimento] = useState("");
  const [sexo, setSexo] = useState("");
  const [objetivo, setObjetivo] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const campos = useErrosDeCampo();
  const recado = useRecado();

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    campos.limpar();
    setEnviando(true);
    try {
      const criado = await api.pacientes.criar({
        nome: nome.trim(),
        email: email.trim() || undefined,
        telefone: telefone.trim() || undefined,
        dataNascimento: dataNascimento || undefined,
        sexo: (sexo || undefined) as never,
        objetivo: objetivo.trim() || undefined,
      });
      recado.confirmar(`${criado.nome} entrou na sua lista de pacientes.`);
      aoCriar(criado.id);
    } catch (e) {
      // Erro de campo gruda no campo; o resto vai para a tira, com a frase que
      // diz o que se tentava fazer. Mostrar os dois repetiria a informação.
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "cadastrar o paciente"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2 style={{ marginBottom: "0.85rem" }}>Novo paciente</h2>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.85rem" }}>
          {erro}
        </div>
      )}

      <div className="grade duas">
        <div className="campo">
          <label htmlFor="np-nome">Nome</label>
          <input
            id="np-nome"
            name="nome"
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            required
            {...campos.props("nome")}
          />
          <ErroDeCampo campo="nome" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="np-email">E-mail</label>
          <input
            id="np-email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            {...campos.props("email")}
          />
          <ErroDeCampo campo="email" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="np-tel">Telefone</label>
          <input
            id="np-tel"
            name="telefone"
            value={telefone}
            onChange={(e) => setTelefone(e.target.value)}
            {...campos.props("telefone")}
          />
          <ErroDeCampo campo="telefone" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="np-nasc">Data de nascimento</label>
          <input
            id="np-nasc"
            type="date"
            value={dataNascimento}
            max={new Date().toISOString().slice(0, 10)}
            onChange={(e) => setDataNascimento(e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="np-sexo">Sexo</label>
          <select id="np-sexo" value={sexo} onChange={(e) => setSexo(e.target.value)}>
            <option value="">Não informado</option>
            <option value="FEMININO">Feminino</option>
            <option value="MASCULINO">Masculino</option>
          </select>
        </div>
        <div className="campo">
          <label htmlFor="np-obj">Objetivo</label>
          <input id="np-obj" value={objetivo} onChange={(e) => setObjetivo(e.target.value)} />
        </div>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Salvando…" : "Cadastrar"}
        </button>
      </div>
    </form>
  );
}
