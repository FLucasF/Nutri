import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { useRecado } from "../componentes/Recado";
import type { Orientacao } from "../api/types";
import { contar } from "../texto";

/**
 * Biblioteca de orientações do consultório.
 *
 * Existe para o nutricionista não reescrever "como montar o prato" a cada
 * paciente. O sistema traz alguns modelos como ponto de partida; eles não são
 * editáveis, pela mesma razão que as tabelas de referência de alimentos não
 * são. Quem quer mudar, duplica.
 */
export default function Orientacoes() {
  const recado = useRecado();
  const [orientacoes, setOrientacoes] = useState<Orientacao[]>([]);
  const [termo, setTermo] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [emEdicao, setEmEdicao] = useState<Orientacao | "nova" | null>(null);

  const buscar = useCallback(async () => {
    setCarregando(true);
    setErro(null);
    try {
      const pagina = await api.orientacoes.listar({ termo: termo.trim() || undefined, size: 50 });
      setOrientacoes(pagina.content);
    } catch (e) {
      setErro(explicarErro(e, "abrir as orientações"));
    } finally {
      setCarregando(false);
    }
  }, [termo]);

  useEffect(() => {
    const relogio = setTimeout(buscar, termo ? 300 : 0);
    return () => clearTimeout(relogio);
  }, [buscar, termo]);

  const minhas = orientacoes.filter((o) => !o.modeloDoSistema);
  const modelos = orientacoes.filter((o) => o.modeloDoSistema);

  async function duplicar(o: Orientacao) {
    try {
      const copia = await api.orientacoes.duplicar(o.id);
      recado.confirmar("Cópia criada na sua biblioteca, pronta para editar.");
      await buscar();
      setEmEdicao(copia);
    } catch (e) {
      setErro(explicarErro(e, "duplicar"));
    }
  }

  async function remover(o: Orientacao) {
    if (!confirm(`Remover "${o.titulo}" da biblioteca?`)) return;
    try {
      await api.orientacoes.remover(o.id);
      recado.confirmar(`"${o.titulo}" saiu da biblioteca. Os planos já entregues seguem com o texto.`);
      await buscar();
    } catch (e) {
      setErro(explicarErro(e, "remover"));
    }
  }

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <h1>Orientações</h1>
          <p>
            {contar(minhas.length, "texto seu", "textos seus")} ·{" "}
            {contar(modelos.length, "modelo do sistema", "modelos do sistema")}
          </p>
        </div>
        <button className="botao" onClick={() => setEmEdicao("nova")}>
          Nova orientação
        </button>
      </div>

      {erro && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          {erro}
        </div>
      )}

      {emEdicao && (
        <Editor
          orientacao={emEdicao === "nova" ? null : emEdicao}
          aoFechar={() => setEmEdicao(null)}
          aoSalvar={async () => {
            setEmEdicao(null);
            await buscar();
          }}
        />
      )}

      <div className="cartao" style={{ marginBottom: "0.9rem" }}>
        <div className="campo">
          <label htmlFor="busca-orientacao">Buscar</label>
          <input
            id="busca-orientacao"
            value={termo}
            onChange={(e) => setTermo(e.target.value)}
            placeholder="Hidratação, rótulo, compras…"
          />
        </div>
      </div>

      {carregando ? (
        <p className="carregando">Carregando…</p>
      ) : orientacoes.length === 0 ? (
        <div className="cartao vazio">
          Nenhuma orientação com esse termo. Use <strong>Nova orientação</strong> para escrever a
          primeira.
        </div>
      ) : (
        <div className="lista-orientacoes">
          {minhas.map((o) => (
            <Cartao
              key={o.id}
              orientacao={o}
              aoEditar={() => setEmEdicao(o)}
              aoDuplicar={() => duplicar(o)}
              aoRemover={() => remover(o)}
            />
          ))}
          {modelos.length > 0 && (
            <p className="minusculo" style={{ margin: "0.6rem 0 0" }}>
              Modelos do sistema — duplique para criar a sua versão.
            </p>
          )}
          {modelos.map((o) => (
            <Cartao key={o.id} orientacao={o} aoDuplicar={() => duplicar(o)} />
          ))}
        </div>
      )}
    </>
  );
}

function Cartao({
  orientacao,
  aoEditar,
  aoDuplicar,
  aoRemover,
}: {
  orientacao: Orientacao;
  aoEditar?: () => void;
  aoDuplicar: () => void;
  aoRemover?: () => void;
}) {
  const [aberta, setAberta] = useState(false);

  return (
    <article className={`cartao orientacao ${orientacao.modeloDoSistema ? "modelo" : ""}`}>
      <header>
        <button type="button" className="titulo-orientacao" onClick={() => setAberta(!aberta)}>
          <h2>{orientacao.titulo}</h2>
        </button>
        {orientacao.modeloDoSistema && <span className="etiqueta">do sistema</span>}
      </header>

      <p className={`corpo-orientacao ${aberta ? "aberta" : ""}`}>{orientacao.corpo}</p>

      {orientacao.temImagem && (
        <Figura
          id={orientacao.id}
          alt={`Figura da orientação ${orientacao.titulo}`}
          className={aberta ? "" : "recolhida"}
        />
      )}

      <div className="linha" style={{ marginTop: "0.6rem" }}>
        <button type="button" className="botao secundario pequeno" onClick={() => setAberta(!aberta)}>
          {aberta ? "Recolher" : "Ler tudo"}
        </button>
        <button type="button" className="botao secundario pequeno" onClick={aoDuplicar}>
          Duplicar
        </button>
        {aoEditar && (
          <button type="button" className="botao secundario pequeno" onClick={aoEditar}>
            Editar
          </button>
        )}
        {aoRemover && (
          <button type="button" className="botao perigo pequeno" onClick={aoRemover}>
            Remover
          </button>
        )}
      </div>
    </article>
  );
}

function Editor({
  orientacao,
  aoFechar,
  aoSalvar,
}: {
  orientacao: Orientacao | null;
  aoFechar: () => void;
  aoSalvar: () => Promise<void>;
}) {
  const [titulo, setTitulo] = useState(orientacao?.titulo ?? "");
  const [corpo, setCorpo] = useState(orientacao?.corpo ?? "");
  const [figura, setFigura] = useState<File | null>(null);
  const recado = useRecado();
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setSalvando(true);
    try {
      const dados = { titulo: titulo.trim(), corpo: corpo.trim() };
      // A figura vai depois do texto porque precisa do identificador — numa
      // orientação nova ele só existe depois de gravada.
      const salva = orientacao
        ? await api.orientacoes.atualizar(orientacao.id, dados)
        : await api.orientacoes.criar(dados);
      if (figura) {
        await api.orientacoes.enviarImagem(salva.id, figura);
      }
      recado.confirmar(`"${salva.titulo}" salva${figura ? ", com a figura anexada" : ""}.`);
      await aoSalvar();
    } catch (e) {
      setErro(explicarErro(e, "salvar"));
      setSalvando(false);
    }
  }

  return (
    <form className="cartao" style={{ marginBottom: "0.9rem" }} onSubmit={enviar}>
      <h2>{orientacao ? "Editar orientação" : "Nova orientação"}</h2>

      {erro && (
        <div className="aviso erro" style={{ margin: "0.8rem 0" }}>
          {erro}
        </div>
      )}

      <div className="campo" style={{ marginTop: "0.8rem" }}>
        <label htmlFor="or-titulo">Título</label>
        <input
          id="or-titulo"
          value={titulo}
          onChange={(e) => setTitulo(e.target.value)}
          required
          maxLength={150}
          placeholder="Como montar o prato"
        />
      </div>

      <div className="campo" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="or-corpo">Texto</label>
        <textarea
          id="or-corpo"
          rows={10}
          value={corpo}
          onChange={(e) => setCorpo(e.target.value)}
          required
          maxLength={8000}
          placeholder="Escreva como falaria com o paciente."
        />
        <span className="minusculo">
          Este texto vai para o paciente como você escrever — no link do plano e no PDF.
        </span>
      </div>

      <div className="campo" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="or-figura">Figura</label>
        <input
          id="or-figura"
          type="file"
          accept="image/*"
          onChange={(e) => setFigura(e.target.files?.[0] ?? null)}
        />
        <span className="minusculo">
          {orientacao?.temImagem
            ? "Esta orientação já tem uma figura. Escolher outra substitui a atual."
            : "Opcional, até 2 MB. Um desenho do prato dividido diz mais que o parágrafo que o descreve."}
        </span>
      </div>

      <div className="linha fim" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="botao secundario" onClick={aoFechar}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={salvando}>
          {salvando ? "Salvando…" : "Salvar"}
        </button>
      </div>
    </form>
  );
}

/**
 * A figura de uma orientação.
 *
 * A rota exige credencial e a tag `img` não manda cabeçalho: o arquivo vem por
 * `fetch` e vira uma URL temporária, liberada ao desmontar. Sem liberar, cada
 * figura aberta fica retida em memória enquanto a aba viver.
 */
function Figura({ id, alt, className = "" }: { id: number; alt: string; className?: string }) {
  const [url, setUrl] = useState<string | null>(null);

  useEffect(() => {
    let vivo = true;
    let criada: string | null = null;

    api.orientacoes
      .imagem(id)
      .then((arquivo) => {
        criada = arquivo.url;
        if (vivo) setUrl(arquivo.url);
        else URL.revokeObjectURL(arquivo.url);
      })
      .catch(() => {
        // Figura que não abre não vira mensagem de erro: o texto da orientação
        // já está na tela e é ele que o paciente precisa.
        if (vivo) setUrl(null);
      });

    return () => {
      vivo = false;
      if (criada) URL.revokeObjectURL(criada);
    };
  }, [id]);

  if (!url) return null;
  return <img className={`figura-orientacao ${className}`} src={url} alt={alt} />;
}
