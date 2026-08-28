import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useRecado } from "../componentes/Recado";
import { formatarBr } from "../api/datas";
import type { UsuarioDaConta } from "../api/types";
import { contar } from "../texto";

/**
 * Equipe do consultório.
 *
 * Existe para o nutricionista não emprestar a própria senha à secretária — que
 * é o que acontece num consultório sem isso, e é pior que qualquer falha de
 * permissão: um login compartilhado torna o registro de auditoria inútil,
 * porque toda ação fica no nome do dono.
 */
export default function Equipe() {
  const [usuarios, setUsuarios] = useState<UsuarioDaConta[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [criando, setCriando] = useState(false);

  const carregar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      setUsuarios(await api.usuarios.listar());
    } catch (e) {
      setErro(explicarErro(e, "abrir a equipe"));
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function alternar(u: UsuarioDaConta) {
    try {
      if (u.ativo) {
        if (!confirm(`Desativar ${u.nome}? A sessão aberta dela cai na hora.`)) return;
        await api.usuarios.inativar(u.id);
      } else {
        await api.usuarios.reativar(u.id);
      }
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "alterar o usuário"));
    }
  }

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Equipe</h1>
          <p>{contar(usuarios.length, "pessoa com acesso", "pessoas com acesso")}</p>
        </div>
        <button className="botao" onClick={() => setCriando((v) => !v)}>
          {criando ? "Cancelar" : "Cadastrar secretária"}
        </button>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {criando && (
        <Formulario
          aoFechar={() => setCriando(false)}
          aoCriar={async () => {
            setCriando(false);
            await carregar();
          }}
        />
      )}

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Nome</th>
                <th>E-mail</th>
                <th>Acesso</th>
                <th>Desde</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((u) => (
                <tr key={u.id}>
                  <td>
                    <strong>{u.nome}</strong>
                  </td>
                  <td className="discreto">{u.email}</td>
                  <td>
                    <span className={`etiqueta ${u.perfil === "NUTRICIONISTA" ? "verde" : ""}`}>
                      {u.perfilDescricao}
                    </span>
                    {!u.ativo && <span className="etiqueta vermelha">inativo</span>}
                  </td>
                  <td className="mono">{formatarBr(u.criadoEm?.slice(0, 10))}</td>
                  <td>
                    {u.perfil !== "NUTRICIONISTA" && (
                      <button
                        className={`botao ${u.ativo ? "perigo" : "secundario"} pequeno`}
                        onClick={() => alternar(u)}
                      >
                        {u.ativo ? "Desativar" : "Reativar"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="cartao" style={{ marginTop: "0.9rem" }}>
        <h2>O que a secretária vê</h2>
        <p className="discreto" style={{ marginTop: "0.4rem", marginBottom: 0 }}>
          Agenda e cadastro de pacientes. Não vê prescrição, antropometria, exames, receitas,
          orientações nem financeiro — prescrever é ato privativo do nutricionista, e o resto é
          dado clínico ou do dono da conta.
        </p>
      </div>
    </>
  );
}

function Formulario({
  aoFechar,
  aoCriar,
}: {
  aoFechar: () => void;
  aoCriar: () => Promise<void>;
}) {
  const [nome, setNome] = useState("");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [telefone, setTelefone] = useState("");
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
      await api.usuarios.criarSecretaria({
        nome: nome.trim(),
        email: email.trim(),
        senhaInicial: senha,
        telefone: telefone.trim() || undefined,
      });
      recado.confirmar(`${nome.trim()} já pode entrar com o e-mail e a senha combinada.`);
      await aoCriar();
    } catch (e) {
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, "cadastrar a secretária"));
      }
      setEnviando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>Cadastrar secretária</h2>

      {erro && (
        <div className="aviso erro" style={{ margin: "0.8rem 0" }}>
          {erro}
        </div>
      )}

      <div className="grade duas" style={{ marginTop: "0.8rem" }}>
        <div className="campo">
          <label htmlFor="eq-nome">Nome</label>
          <input
            id="eq-nome"
            name="nome"
            value={nome}
            onChange={(e) => setNome(e.target.value)}
            required
            {...campos.props("nome")}
          />
          <ErroDeCampo campo="nome" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="eq-email">E-mail</label>
          <input
            id="eq-email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            {...campos.props("email")}
          />
          <ErroDeCampo campo="email" erros={campos.erros} />
        </div>
        <div className="campo">
          <label htmlFor="eq-senha">Senha inicial</label>
          <input
            id="eq-senha"
            name="senhaInicial"
            type="password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            required
            minLength={8}
            {...campos.props("senhaInicial")}
          />
          <ErroDeCampo campo="senhaInicial" erros={campos.erros} />
          <span className="minusculo">
            Combine pessoalmente. Ela pode trocar depois em “Esqueci minha senha”.
          </span>
        </div>
        <div className="campo">
          <label htmlFor="eq-tel">Telefone</label>
          <input
            id="eq-tel"
            name="telefone"
            value={telefone}
            onChange={(e) => setTelefone(e.target.value)}
            {...campos.props("telefone")}
          />
          <ErroDeCampo campo="telefone" erros={campos.erros} />
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
