import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import BuscaDeAlimento from "../componentes/BuscaDeAlimento";
import { ErroApi, api } from "../api/client";
import { explicarErro, ondeOlhar } from "../api/erros";
import { useRecado } from "../componentes/Recado";
import { CORES_MACRO, MACROS_PRINCIPAIS, NUTRIENTES, formatarNutriente, rotuloDe } from "../api/nutrientes";
import type {
  AlimentoDetalhe,
  AlimentoResumo,
  MetodoPrescricao,
  Orientacao,
  OrientacaoDoPlano,
  PacienteResumo,
  PlanoRequest,
  PlanoResponse,
  Total,
} from "../api/types";

/** Estado local de edição — espelha o corpo que a API espera. */
interface ItemEdicao {
  chave: string;
  alimentoId?: number;
  medidaId?: number;
  descricao: string;
  quantidade: string;
  observacao: string;
  /** Porções do alimento, carregadas ao escolhê-lo. */
  medidas: { id: number; descricao: string; gramas: number; padrao: boolean }[];
  equivalentes: { chave: string; descricao: string; quantidade: string; alimentoId?: number; medidaId?: number }[];
}

interface RefeicaoEdicao {
  chave: string;
  nome: string;
  horario: string;
  observacao: string;
  itens: ItemEdicao[];
}

let contador = 0;
const novaChave = () => `k${++contador}`;

const REFEICOES_SUGERIDAS = [
  { nome: "Café da manhã", horario: "07:30" },
  { nome: "Lanche da manhã", horario: "10:00" },
  { nome: "Almoço", horario: "12:30" },
  { nome: "Lanche da tarde", horario: "16:00" },
  { nome: "Jantar", horario: "19:30" },
  { nome: "Ceia", horario: "21:30" },
];

export default function EditorDePlano() {
  const { id } = useParams();
  const [parametros] = useSearchParams();
  const navegar = useNavigate();
  const planoId = id ? Number(id) : undefined;

  const [plano, setPlano] = useState<PlanoResponse | null>(null);
  const [pacientes, setPacientes] = useState<PacienteResumo[]>([]);

  const [titulo, setTitulo] = useState("");
  const [pacienteId, setPacienteId] = useState(parametros.get("pacienteId") ?? "");
  const [metodo, setMetodo] = useState<MetodoPrescricao>("ALIMENTOS");
  const [modelo, setModelo] = useState(false);
  // A meta pode chegar pela URL, vinda do gasto energético estimado na
  // antropometria (RF68): sem isso o número é lido numa tela e redigitado
  // noutra, que é onde o erro de digitação entra.
  const [metaEnergia, setMetaEnergia] = useState(parametros.get("meta") ?? "");
  const [vigenciaInicio, setVigenciaInicio] = useState("");
  const [vigenciaFim, setVigenciaFim] = useState("");
  const [orientacoes, setOrientacoes] = useState("");
  const [observacoesInternas, setObservacoesInternas] = useState("");
  const [refeicoes, setRefeicoes] = useState<RefeicaoEdicao[]>([]);

  const [carregando, setCarregando] = useState(!!planoId);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const avisosDoPlano = useRecado();
  /** Erros de validação do plano, um por linha e já localizados na tela. */
  const [problemas, setProblemas] = useState<string[]>([]);
  const [sujo, setSujo] = useState(false);

  useEffect(() => {
    api.pacientes
      .listar({ ativo: true, size: 200 })
      .then((p) => setPacientes(p.content))
      .catch(() => setPacientes([]));
  }, []);

  /**
   * Recarrega as porções dos alimentos já usados no plano.
   *
   * O plano gravado guarda só o id da medida, não a lista de porções do
   * alimento. Sem buscá-las de volta, o seletor de medida abriria vazio e o
   * peso mostrado na linha cairia para a quantidade crua — "4 g" no lugar de
   * "4 colheres = 100 g". O total do servidor continua certo; o que quebra é
   * a leitura da tela.
   */
  const recarregarMedidas = useCallback(async (dados: PlanoResponse) => {
    const ids = [
      ...new Set(
        dados.refeicoes.flatMap((r) =>
          r.itens.map((i) => i.alimentoId).filter((x): x is number => x !== undefined),
        ),
      ),
    ];
    if (ids.length === 0) return;

    const detalhes = await Promise.all(
      ids.map((idAlimento) =>
        api.alimentos.detalhar(idAlimento).catch(() => null),
      ),
    );
    const porAlimento = new Map<number, ItemEdicao["medidas"]>();
    detalhes.forEach((detalhe) => {
      if (detalhe) {
        porAlimento.set(
          detalhe.id,
          detalhe.medidas.map((m) => ({
            id: m.id,
            descricao: m.descricao,
            gramas: m.gramas,
            padrao: m.padrao,
          })),
        );
      }
    });

    setRefeicoes((atual) =>
      atual.map((r) => ({
        ...r,
        itens: r.itens.map((i) =>
          i.alimentoId && porAlimento.has(i.alimentoId)
            ? { ...i, medidas: porAlimento.get(i.alimentoId)! }
            : i,
        ),
      })),
    );
  }, []);

  const aplicarPlano = useCallback((dados: PlanoResponse) => {
    setPlano(dados);
    setTitulo(dados.titulo);
    setPacienteId(dados.pacienteId ? String(dados.pacienteId) : "");
    setMetodo(dados.metodo);
    setModelo(dados.modelo);
    setMetaEnergia(dados.metaEnergiaKcal ? String(dados.metaEnergiaKcal) : "");
    setVigenciaInicio(dados.vigenciaInicio ?? "");
    setVigenciaFim(dados.vigenciaFim ?? "");
    setOrientacoes(dados.orientacoes ?? "");
    setObservacoesInternas(dados.observacoesInternas ?? "");
    setRefeicoes(
      dados.refeicoes.map((r) => ({
        chave: novaChave(),
        nome: r.nome,
        horario: r.horario?.slice(0, 5) ?? "",
        observacao: r.observacao ?? "",
        itens: r.itens.map((i) => ({
          chave: novaChave(),
          alimentoId: i.alimentoId,
          medidaId: i.medidaId,
          descricao: i.descricao,
          quantidade: i.quantidade !== undefined && i.quantidade !== null ? String(i.quantidade) : "",
          observacao: i.observacao ?? "",
          medidas: [],
          equivalentes: i.equivalentes.map((e) => ({
            chave: novaChave(),
            descricao: e.descricao,
            quantidade: "",
            alimentoId: e.alimentoId,
          })),
        })),
      })),
    );
    setSujo(false);
    void recarregarMedidas(dados);
  }, [recarregarMedidas]);

  useEffect(() => {
    if (!planoId) return;
    setCarregando(true);
    api.prescricoes
      .detalhar(planoId)
      .then(aplicarPlano)
      .catch((e) => setErro(explicarErro(e, "abrir o plano")))
      .finally(() => setCarregando(false));
  }, [planoId, aplicarPlano]);

  function montarCorpo(): PlanoRequest {
    return {
      titulo: titulo.trim(),
      pacienteId: modelo ? undefined : pacienteId ? Number(pacienteId) : undefined,
      metodo,
      modelo,
      metaEnergiaKcal: metaEnergia ? Number(metaEnergia.replace(",", ".")) : undefined,
      vigenciaInicio: vigenciaInicio || undefined,
      vigenciaFim: vigenciaFim || undefined,
      orientacoes: orientacoes.trim() || undefined,
      observacoesInternas: observacoesInternas.trim() || undefined,
      refeicoes: refeicoes.map((r) => ({
        nome: r.nome.trim() || "Refeição",
        horario: r.horario ? `${r.horario}:00` : undefined,
        observacao: r.observacao.trim() || undefined,
        itens: r.itens
          .filter((i) => i.alimentoId || i.descricao.trim())
          .map((i) => ({
            alimentoId: i.alimentoId,
            medidaId: i.medidaId,
            descricao: i.descricao.trim() || undefined,
            quantidade: i.quantidade ? Number(i.quantidade.replace(",", ".")) : undefined,
            observacao: i.observacao.trim() || undefined,
            equivalentes:
              metodo === "EQUIVALENTES"
                ? i.equivalentes
                    .filter((e) => e.descricao.trim())
                    .map((e) => ({
                      alimentoId: e.alimentoId,
                      medidaId: e.medidaId,
                      descricao: e.descricao.trim(),
                      quantidade: e.quantidade ? Number(e.quantidade.replace(",", ".")) : undefined,
                    }))
                : undefined,
          })),
      })),
    };
  }

  async function salvar() {
    setErro(null);
    setSalvando(true);
    try {
      const corpo = montarCorpo();
      const salvo = planoId
        ? await api.prescricoes.atualizar(planoId, corpo)
        : await api.prescricoes.criar(corpo);

      aplicarPlano(salvo);
      setProblemas([]);
      avisosDoPlano.confirmar("Plano salvo.");
      if (!planoId) {
        navegar(`/prescricoes/${salvo.id}`, { replace: true });
      }
    } catch (e) {
      // O plano não tem um campo por erro: a validação aponta para dentro das
      // refeições, e o caminho técnico não diz onde olhar na tela.
      if (e instanceof ErroApi && e.ehValidacao) {
        setProblemas(
          e.campos!.map((c) => {
            const onde = ondeOlhar(c.campo, (i) => refeicoes[i]?.nome);
            return onde ? `${onde}: ${c.mensagem}` : c.mensagem;
          }),
        );
      } else {
        setErro(explicarErro(e, "salvar o plano"));
      }
    } finally {
      setSalvando(false);
    }
  }

  async function acao(executar: () => Promise<PlanoResponse>, mensagem: string) {
    setErro(null);
    try {
      aplicarPlano(await executar());
      avisosDoPlano.confirmar(mensagem);
    } catch (e) {
      setErro(explicarErro(e, "concluir a ação"));
    }
  }

  // ------------------------------------------------------------- manipulação

  function alterarRefeicao(chave: string, mudanca: Partial<RefeicaoEdicao>) {
    setSujo(true);
    setRefeicoes((atual) => atual.map((r) => (r.chave === chave ? { ...r, ...mudanca } : r)));
  }

  function alterarItem(chaveRefeicao: string, chaveItem: string, mudanca: Partial<ItemEdicao>) {
    setSujo(true);
    setRefeicoes((atual) =>
      atual.map((r) =>
        r.chave !== chaveRefeicao
          ? r
          : { ...r, itens: r.itens.map((i) => (i.chave === chaveItem ? { ...i, ...mudanca } : i)) },
      ),
    );
  }

  function adicionarRefeicao(nome = "Nova refeição", horario = "") {
    setSujo(true);
    setRefeicoes((atual) => [
      ...atual,
      { chave: novaChave(), nome, horario, observacao: "", itens: [] },
    ]);
  }

  function removerRefeicao(chave: string) {
    setSujo(true);
    setRefeicoes((atual) => atual.filter((r) => r.chave !== chave));
  }

  function adicionarItem(chaveRefeicao: string) {
    setSujo(true);
    setRefeicoes((atual) =>
      atual.map((r) =>
        r.chave !== chaveRefeicao
          ? r
          : {
              ...r,
              itens: [
                ...r.itens,
                {
                  chave: novaChave(),
                  descricao: "",
                  quantidade: "",
                  observacao: "",
                  medidas: [],
                  equivalentes: [],
                },
              ],
            },
      ),
    );
  }

  function removerItem(chaveRefeicao: string, chaveItem: string) {
    setSujo(true);
    setRefeicoes((atual) =>
      atual.map((r) =>
        r.chave !== chaveRefeicao ? r : { ...r, itens: r.itens.filter((i) => i.chave !== chaveItem) },
      ),
    );
  }

  /** Ao escolher um alimento, já traz as porções e pré-seleciona a padrão. */
  async function escolherAlimento(chaveRefeicao: string, chaveItem: string, resumo: AlimentoResumo) {
    alterarItem(chaveRefeicao, chaveItem, {
      alimentoId: resumo.id,
      descricao: resumo.descricao,
    });
    try {
      const detalhe: AlimentoDetalhe = await api.alimentos.detalhar(resumo.id);
      const padrao = detalhe.medidas.find((m) => m.padrao) ?? detalhe.medidas[0];
      alterarItem(chaveRefeicao, chaveItem, {
        medidas: detalhe.medidas.map((m) => ({
          id: m.id,
          descricao: m.descricao,
          gramas: m.gramas,
          padrao: m.padrao,
        })),
        medidaId: padrao?.id,
        quantidade: padrao ? "1" : "100",
      });
    } catch {
      alterarItem(chaveRefeicao, chaveItem, { medidas: [], quantidade: "100" });
    }
  }

  const totalSalvo = plano?.totalDoDia;
  const totaisPorRefeicao = useMemo(() => {
    const mapa = new Map<number, Total>();
    plano?.refeicoes.forEach((r, indice) => mapa.set(indice, r.total));
    return mapa;
  }, [plano]);

  if (carregando) return <p className="carregando">Carregando…</p>;

  const podeEditar = !plano || plano.status !== "ENCERRADO";

  return (
    <>
      <div className="cabecalho-pagina">
        <div>
          <Link to="/prescricoes" className="minusculo">
            ← Prescrições
          </Link>
          <h1 style={{ marginTop: "0.2rem" }}>{planoId ? titulo || "Plano" : "Novo plano"}</h1>
          <p>
            {plano ? (
              <>
                {plano.statusDescricao} · {plano.metodoDescricao}
                {plano.pacienteNome ? ` · ${plano.pacienteNome}` : ""}
              </>
            ) : (
              "Monte as refeições e salve para ver os totais calculados."
            )}
          </p>
        </div>
        <div className="linha">
          {sujo && <span className="etiqueta ambar">alterações não salvas</span>}
          <button className="botao" onClick={salvar} disabled={salvando || !podeEditar}>
            {salvando ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </div>

      {erro && <div className="aviso erro" style={{ marginBottom: "0.9rem" }}>{erro}</div>}

      {problemas.length > 0 && (
        <div className="aviso erro" role="alert" style={{ marginBottom: "0.9rem" }}>
          <strong>O plano não foi salvo. Corrija {problemas.length === 1 ? "isto" : "estes pontos"}:</strong>
          <ul className="lista-de-problemas">
            {problemas.map((p, i) => (
              <li key={i}>{p}</li>
            ))}
          </ul>
        </div>
      )}
      {!podeEditar && (
        <div className="aviso atencao" style={{ marginBottom: "0.9rem" }}>
          Este plano está encerrado e não aceita alterações. Duplique-o para criar uma nova versão.
        </div>
      )}

      <div className="editor-plano">
        <div>
          {/* ------------------------------------------------ cabeçalho do plano */}
          <div className="cartao" style={{ marginBottom: "0.9rem" }}>
            <div className="grade duas">
              <div className="campo" style={{ gridColumn: "span 2" }}>
                <label htmlFor="pl-titulo">Título do plano</label>
                <input
                  id="pl-titulo"
                  value={titulo}
                  onChange={(e) => {
                    setTitulo(e.target.value);
                    setSujo(true);
                  }}
                  placeholder="Plano de emagrecimento — fase 1"
                  required
                />
              </div>

              <div className="campo">
                <label htmlFor="pl-paciente">Paciente</label>
                <select
                  id="pl-paciente"
                  value={pacienteId}
                  disabled={modelo}
                  onChange={(e) => {
                    setPacienteId(e.target.value);
                    setSujo(true);
                  }}
                >
                  <option value="">Selecione…</option>
                  {pacientes.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nome}
                    </option>
                  ))}
                </select>
                {modelo && <span className="minusculo">Modelo não pertence a um paciente.</span>}
              </div>

              <div className="campo">
                <label htmlFor="pl-metodo">Método</label>
                <select
                  id="pl-metodo"
                  value={metodo}
                  onChange={(e) => {
                    setMetodo(e.target.value as MetodoPrescricao);
                    setSujo(true);
                  }}
                >
                  <option value="ALIMENTOS">Por alimentos</option>
                  <option value="EQUIVALENTES">Por equivalentes (com substituições)</option>
                  <option value="QUALITATIVO">Qualitativo (sem quantificar)</option>
                </select>
              </div>

              <div className="campo">
                <label htmlFor="pl-meta">Meta energética (kcal/dia)</label>
                <input
                  id="pl-meta"
                  inputMode="decimal"
                  value={metaEnergia}
                  onChange={(e) => {
                    setMetaEnergia(e.target.value);
                    setSujo(true);
                  }}
                  placeholder="2000"
                />
              </div>

              <div className="campo">
                <label>Vigência</label>
                <div className="par-de-datas">
                  <input
                    type="date"
                    value={vigenciaInicio}
                    onChange={(e) => {
                      setVigenciaInicio(e.target.value);
                      setSujo(true);
                    }}
                    aria-label="Início da vigência"
                  />
                  <input
                    type="date"
                    value={vigenciaFim}
                    onChange={(e) => {
                      setVigenciaFim(e.target.value);
                      setSujo(true);
                    }}
                    aria-label="Fim da vigência"
                  />
                </div>
              </div>
            </div>

            <label className="linha" style={{ gap: "0.4rem", marginTop: "0.8rem" }}>
              <input
                type="checkbox"
                checked={modelo}
                onChange={(e) => {
                  setModelo(e.target.checked);
                  setSujo(true);
                }}
                style={{ width: "auto" }}
              />
              <span className="discreto">
                Salvar como modelo reaproveitável (sem paciente vinculado)
              </span>
            </label>

            <div className="campo" style={{ marginTop: "0.8rem" }}>
              <label htmlFor="pl-orient">Orientações ao paciente</label>
              <textarea
                id="pl-orient"
                rows={2}
                value={orientacoes}
                onChange={(e) => {
                  setOrientacoes(e.target.value);
                  setSujo(true);
                }}
                placeholder="Aparece no plano que o paciente abre."
              />
            </div>

            <div className="campo" style={{ marginTop: "0.6rem" }}>
              <label htmlFor="pl-interno">Anotações internas</label>
              <textarea
                id="pl-interno"
                rows={2}
                value={observacoesInternas}
                onChange={(e) => {
                  setObservacoesInternas(e.target.value);
                  setSujo(true);
                }}
                placeholder="Nunca sai no link do paciente."
              />
            </div>
          </div>

          {/* ------------------------------------------------------- refeições */}
          {refeicoes.length === 0 && (
            <div className="cartao" style={{ marginBottom: "0.9rem" }}>
              <p className="discreto" style={{ marginTop: 0 }}>
                Comece adicionando as refeições do dia:
              </p>
              <div className="linha">
                {REFEICOES_SUGERIDAS.map((s) => (
                  <button
                    key={s.nome}
                    className="botao secundario pequeno"
                    onClick={() => adicionarRefeicao(s.nome, s.horario)}
                  >
                    + {s.nome}
                  </button>
                ))}
              </div>
              <div className="linha" style={{ marginTop: "0.6rem" }}>
                <button
                  className="botao secundario pequeno"
                  onClick={() => REFEICOES_SUGERIDAS.forEach((s) => adicionarRefeicao(s.nome, s.horario))}
                >
                  Adicionar as seis
                </button>
              </div>
            </div>
          )}

          {refeicoes.map((refeicao, indice) => (
            <BlocoRefeicao
              key={refeicao.chave}
              refeicao={refeicao}
              metodo={metodo}
              total={totaisPorRefeicao.get(indice)}
              somenteLeitura={!podeEditar}
              aoAlterar={(mudanca) => alterarRefeicao(refeicao.chave, mudanca)}
              aoRemover={() => removerRefeicao(refeicao.chave)}
              aoAdicionarItem={() => adicionarItem(refeicao.chave)}
              aoAlterarItem={(chaveItem, mudanca) => alterarItem(refeicao.chave, chaveItem, mudanca)}
              aoRemoverItem={(chaveItem) => removerItem(refeicao.chave, chaveItem)}
              aoEscolherAlimento={(chaveItem, resumo) =>
                escolherAlimento(refeicao.chave, chaveItem, resumo)
              }
            />
          ))}

          {refeicoes.length > 0 && podeEditar && (
            <button className="botao secundario" onClick={() => adicionarRefeicao()}>
              + Adicionar refeição
            </button>
          )}
        </div>

        {/* --------------------------------------------------- painel de totais */}
        <aside className="painel-totais">
          <PainelTotais total={totalSalvo} sujo={sujo} meta={metaEnergia} />
          {plano && <PainelOrientacoes planoId={plano.id} />}
          {plano && !plano.modelo && <PainelPublicacao plano={plano} aoExecutar={acao} />}
          {plano && (
            <div className="cartao">
              <h3>Ações</h3>
              <div className="linha" style={{ marginTop: "0.5rem" }}>
                <button
                  className="botao secundario pequeno"
                  onClick={() =>
                    acao(
                      () => api.prescricoes.duplicar(plano.id, plano.pacienteId ?? undefined),
                      "Cópia criada como rascunho.",
                    ).then(() => navegar("/prescricoes"))
                  }
                >
                  Duplicar
                </button>
                <button
                  className="botao perigo pequeno"
                  onClick={async () => {
                    if (!confirm("Remover este plano definitivamente?")) return;
                    await api.prescricoes.remover(plano.id);
                    navegar("/prescricoes");
                  }}
                >
                  Remover
                </button>
              </div>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}

// --------------------------------------------------------------- subcomponentes

function BlocoRefeicao({
  refeicao,
  metodo,
  total,
  somenteLeitura,
  aoAlterar,
  aoRemover,
  aoAdicionarItem,
  aoAlterarItem,
  aoRemoverItem,
  aoEscolherAlimento,
}: {
  refeicao: RefeicaoEdicao;
  metodo: MetodoPrescricao;
  total?: Total;
  somenteLeitura: boolean;
  aoAlterar: (mudanca: Partial<RefeicaoEdicao>) => void;
  aoRemover: () => void;
  aoAdicionarItem: () => void;
  aoAlterarItem: (chaveItem: string, mudanca: Partial<ItemEdicao>) => void;
  aoRemoverItem: (chaveItem: string) => void;
  aoEscolherAlimento: (chaveItem: string, resumo: AlimentoResumo) => void;
}) {
  const quantifica = metodo !== "QUALITATIVO";

  return (
    <div className="refeicao">
      <div className="refeicao-topo">
        <input
          type="text"
          value={refeicao.nome}
          onChange={(e) => aoAlterar({ nome: e.target.value })}
          disabled={somenteLeitura}
          aria-label="Nome da refeição"
        />
        <input
          type="time"
          value={refeicao.horario}
          onChange={(e) => aoAlterar({ horario: e.target.value })}
          disabled={somenteLeitura}
          aria-label="Horário"
        />
        {total && total.composicao.energiaKcal !== undefined && (
          <span className="etiqueta verde">{Math.round(total.composicao.energiaKcal)} kcal</span>
        )}
        {!somenteLeitura && (
          <button className="botao perigo pequeno" onClick={aoRemover}>
            Remover
          </button>
        )}
      </div>

      {refeicao.itens.length === 0 ? (
        <p className="vazio" style={{ padding: "1rem" }}>
          Busque um alimento abaixo para montar esta refeição.
        </p>
      ) : (
        refeicao.itens.map((item) => (
          <LinhaItem
            key={item.chave}
            item={item}
            quantifica={quantifica}
            admiteEquivalentes={metodo === "EQUIVALENTES"}
            somenteLeitura={somenteLeitura}
            aoAlterar={(mudanca) => aoAlterarItem(item.chave, mudanca)}
            aoRemover={() => aoRemoverItem(item.chave)}
            aoEscolherAlimento={(resumo) => aoEscolherAlimento(item.chave, resumo)}
          />
        ))
      )}

      {!somenteLeitura && (
        <div style={{ padding: "0.6rem 0.9rem" }}>
          <button className="botao secundario pequeno" onClick={aoAdicionarItem}>
            + Adicionar item
          </button>
        </div>
      )}
    </div>
  );
}

function LinhaItem({
  item,
  quantifica,
  admiteEquivalentes,
  somenteLeitura,
  aoAlterar,
  aoRemover,
  aoEscolherAlimento,
}: {
  item: ItemEdicao;
  quantifica: boolean;
  admiteEquivalentes: boolean;
  somenteLeitura: boolean;
  aoAlterar: (mudanca: Partial<ItemEdicao>) => void;
  aoRemover: () => void;
  aoEscolherAlimento: (resumo: AlimentoResumo) => void;
}) {
  const medidaEscolhida = item.medidas.find((m) => m.id === item.medidaId);
  const quantidade = Number(item.quantidade.replace(",", "."));
  const gramas =
    medidaEscolhida && Number.isFinite(quantidade)
      ? medidaEscolhida.gramas * quantidade
      : Number.isFinite(quantidade)
        ? quantidade
        : undefined;

  return (
    <div className="item-linha">
      <div className="descricao-item">
        {item.alimentoId ? (
          <>
            <strong>{item.descricao}</strong>
            {!somenteLeitura && (
              <small>
                <button
                  className="botao secundario pequeno"
                  style={{ marginTop: "0.2rem" }}
                  onClick={() => aoAlterar({ alimentoId: undefined, medidaId: undefined, medidas: [] })}
                >
                  trocar alimento
                </button>
              </small>
            )}
          </>
        ) : (
          <BuscaDeAlimento
            valorLivre={item.descricao}
            aoDigitarLivre={(texto) => aoAlterar({ descricao: texto })}
            aoEscolher={aoEscolherAlimento}
            desabilitado={somenteLeitura}
          />
        )}
      </div>

      {quantifica ? (
        <>
          <input
            inputMode="decimal"
            value={item.quantidade}
            onChange={(e) => aoAlterar({ quantidade: e.target.value })}
            placeholder="qtd"
            disabled={somenteLeitura}
            aria-label="Quantidade"
          />
          <select
            value={item.medidaId ?? ""}
            onChange={(e) =>
              aoAlterar({ medidaId: e.target.value ? Number(e.target.value) : undefined })
            }
            disabled={somenteLeitura || item.medidas.length === 0}
            aria-label="Medida"
          >
            <option value="">gramas</option>
            {item.medidas.map((m) => (
              <option key={m.id} value={m.id}>
                {m.descricao}
              </option>
            ))}
          </select>
          <span className="mono discreto" style={{ textAlign: "right" }}>
            {gramas !== undefined && gramas > 0 ? `${arredondar(gramas)} g` : "—"}
          </span>
        </>
      ) : (
        <>
          <span className="discreto" style={{ gridColumn: "span 3" }}>
            à vontade / sem quantificar
          </span>
        </>
      )}

      {!somenteLeitura ? (
        <button className="botao perigo pequeno" onClick={aoRemover} aria-label="Remover item">
          ×
        </button>
      ) : (
        <span />
      )}

      {admiteEquivalentes && item.alimentoId && (
        <div style={{ gridColumn: "1 / -1", paddingLeft: "0.4rem" }}>
          <Equivalentes item={item} somenteLeitura={somenteLeitura} aoAlterar={aoAlterar} />
        </div>
      )}
    </div>
  );
}

function Equivalentes({
  item,
  somenteLeitura,
  aoAlterar,
}: {
  item: ItemEdicao;
  somenteLeitura: boolean;
  aoAlterar: (mudanca: Partial<ItemEdicao>) => void;
}) {
  return (
    <div style={{ borderLeft: "2px solid var(--line-strong)", paddingLeft: "0.6rem", marginTop: "0.3rem" }}>
      <span className="minusculo">Substituições</span>
      {item.equivalentes.map((equivalente) => (
        <div className="linha" key={equivalente.chave} style={{ gap: "0.4rem", marginTop: "0.25rem" }}>
          <input
            style={{ flex: 2, minWidth: 140 }}
            value={equivalente.descricao}
            placeholder="1 tapioca média"
            disabled={somenteLeitura}
            onChange={(e) =>
              aoAlterar({
                equivalentes: item.equivalentes.map((x) =>
                  x.chave === equivalente.chave ? { ...x, descricao: e.target.value } : x,
                ),
              })
            }
          />
          <input
            style={{ width: 90 }}
            inputMode="decimal"
            value={equivalente.quantidade}
            placeholder="g"
            disabled={somenteLeitura}
            onChange={(e) =>
              aoAlterar({
                equivalentes: item.equivalentes.map((x) =>
                  x.chave === equivalente.chave ? { ...x, quantidade: e.target.value } : x,
                ),
              })
            }
          />
          {!somenteLeitura && (
            <button
              className="botao perigo pequeno"
              onClick={() =>
                aoAlterar({
                  equivalentes: item.equivalentes.filter((x) => x.chave !== equivalente.chave),
                })
              }
            >
              ×
            </button>
          )}
        </div>
      ))}
      {!somenteLeitura && (
        <button
          className="botao secundario pequeno"
          style={{ marginTop: "0.3rem" }}
          onClick={() =>
            aoAlterar({
              equivalentes: [
                ...item.equivalentes,
                { chave: novaChave(), descricao: "", quantidade: "" },
              ],
            })
          }
        >
          + substituição
        </button>
      )}
    </div>
  );
}


function PainelTotais({ total, sujo, meta }: { total?: Total; sujo: boolean; meta: string }) {
  if (!total) {
    return (
      <div className="cartao">
        <h3>Totais do dia</h3>
        <p className="discreto" style={{ marginBottom: 0 }}>
          Salve o plano para calcular.
        </p>
      </div>
    );
  }

  const distribuicao = total.distribuicao;
  const metaNumero = meta ? Number(meta.replace(",", ".")) : undefined;

  return (
    <div className="cartao">
      <h3>Totais do dia</h3>

      {sujo && (
        <div className="aviso atencao" style={{ margin: "0.5rem 0", fontSize: "0.8rem" }}>
          Há alterações não salvas. Os números abaixo referem-se à última versão salva.
        </div>
      )}

      <div style={{ marginTop: "0.5rem" }}>
        {MACROS_PRINCIPAIS.map((chave) => {
          const definicao = NUTRIENTES.find((n) => n.chave === chave)!;
          const valor = total.composicao[chave];
          const ausente = valor === undefined || valor === null;
          return (
            <div key={chave} className={`nutriente-linha ${ausente ? "ausente" : ""}`}>
              <span>{definicao.rotulo}</span>
              <span>{formatarNutriente(valor, definicao.unidade)}</span>
            </div>
          );
        })}
      </div>

      {distribuicao && (
        <div style={{ marginTop: "0.9rem" }}>
          <span className="minusculo">Distribuição energética</span>
          <div className="barra-macro" style={{ marginTop: "0.3rem" }}>
            <i style={{ width: `${distribuicao.proteinaPct}%`, background: CORES_MACRO.proteina }} />
            <i style={{ width: `${distribuicao.carboidratoPct}%`, background: CORES_MACRO.carboidrato }} />
            <i style={{ width: `${distribuicao.lipideoPct}%`, background: CORES_MACRO.lipideo }} />
          </div>
          <div className="legenda-macro" style={{ marginTop: "0.35rem" }}>
            <span>
              <i style={{ background: CORES_MACRO.proteina }} />
              Prot. {distribuicao.proteinaPct}%
            </span>
            <span>
              <i style={{ background: CORES_MACRO.carboidrato }} />
              Carb. {distribuicao.carboidratoPct}%
            </span>
            <span>
              <i style={{ background: CORES_MACRO.lipideo }} />
              Gord. {distribuicao.lipideoPct}%
            </span>
          </div>
        </div>
      )}

      {total.adequacaoEnergeticaPct !== undefined && metaNumero ? (
        <p className="discreto" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
          <strong>{total.adequacaoEnergeticaPct}%</strong> da meta de {metaNumero} kcal.
        </p>
      ) : null}

      {/*
        A ressalva importa: quando parte dos itens não tem o nutriente
        determinado na fonte, o total é um piso, e apresentá-lo como número
        exato induziria o profissional ao erro.
      */}
      {total.nutrientesIncompletos.length > 0 && (
        <div className="aviso atencao" style={{ marginTop: "0.8rem", fontSize: "0.8rem" }}>
          <strong>Total parcial.</strong> Nem todos os itens têm dado para{" "}
          {total.nutrientesIncompletos.slice(0, 4).map(rotuloDe).join(", ")}
          {total.nutrientesIncompletos.length > 4
            ? ` e mais ${total.nutrientesIncompletos.length - 4}`
            : ""}
          . Os valores são um piso, não um total exato.
        </div>
      )}

      {total.itensForaDoCalculo > 0 && (
        <p className="minusculo" style={{ marginTop: "0.6rem", marginBottom: 0 }}>
          {total.itensForaDoCalculo} item(ns) sem quantidade não entraram no cálculo.
        </p>
      )}
    </div>
  );
}

/**
 * Orientações anexadas a este plano.
 *
 * O texto é copiado da biblioteca no momento do anexo, e não referenciado:
 * corrigir um modelo depois não pode mudar o que o paciente já recebeu. A cópia
 * também é o que permite adaptar o texto a este paciente sem sujar o modelo.
 */
function PainelOrientacoes({ planoId }: { planoId: number }) {
  const [anexadas, setAnexadas] = useState<OrientacaoDoPlano[]>([]);
  const [biblioteca, setBiblioteca] = useState<Orientacao[]>([]);
  const [escolhida, setEscolhida] = useState("");
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async () => {
    try {
      setAnexadas(await api.orientacoes.doPlano(planoId));
    } catch {
      setAnexadas([]);
    }
  }, [planoId]);

  useEffect(() => {
    void carregar();
    api.orientacoes
      .listar({ size: 100 })
      .then((p) => setBiblioteca(p.content))
      .catch(() => setBiblioteca([]));
  }, [carregar]);

  async function anexar() {
    if (!escolhida) return;
    setErro(null);
    try {
      await api.orientacoes.anexar(planoId, { orientacaoId: Number(escolhida) });
      setEscolhida("");
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "anexar a orientação"));
    }
  }

  async function desanexar(anexoId: number) {
    try {
      await api.orientacoes.desanexar(planoId, anexoId);
      await carregar();
    } catch (e) {
      setErro(explicarErro(e, "remover"));
    }
  }

  const disponiveis = biblioteca.filter(
    (o) => !anexadas.some((a) => a.orientacaoId === o.id),
  );

  return (
    <div className="cartao">
      <h3>Orientações</h3>

      {erro && (
        <div className="aviso erro" style={{ margin: "0.5rem 0" }}>
          {erro}
        </div>
      )}

      {anexadas.length === 0 ? (
        <p className="minusculo" style={{ marginTop: "0.4rem" }}>
          Nenhuma anexada. O paciente recebe estes textos junto do plano, no link e no PDF.
        </p>
      ) : (
        anexadas.map((a) => (
          <div className="linha" key={a.id} style={{ marginTop: "0.5rem", gap: "0.4rem" }}>
            <span style={{ flex: 1, fontSize: "0.88rem" }}>{a.titulo}</span>
            {/* A figura foi copiada junto: quem monta o plano precisa saber
                que o paciente vai receber o desenho, não só o texto. */}
            {a.temImagem && <span className="etiqueta">com figura</span>}
            <button
              type="button"
              className="botao perigo pequeno"
              onClick={() => desanexar(a.id)}
            >
              Tirar
            </button>
          </div>
        ))
      )}

      <div className="linha" style={{ marginTop: "0.7rem", gap: "0.4rem" }}>
        <select
          value={escolhida}
          onChange={(e) => setEscolhida(e.target.value)}
          aria-label="Orientação da biblioteca"
          style={{
            flex: 1,
            fontSize: "0.84rem",
            padding: "0.3rem 0.45rem",
            border: "1px solid var(--contorno-campo)",
            borderRadius: "var(--raio)",
          }}
        >
          <option value="">Escolher da biblioteca…</option>
          {disponiveis.map((o) => (
            <option key={o.id} value={o.id}>
              {o.titulo}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="botao secundario pequeno"
          onClick={anexar}
          disabled={!escolhida}
        >
          Anexar
        </button>
      </div>
    </div>
  );
}

function PainelPublicacao({
  plano,
  aoExecutar,
}: {
  plano: PlanoResponse;
  aoExecutar: (executar: () => Promise<PlanoResponse>, mensagem: string) => Promise<void>;
}) {
  const [copiado, setCopiado] = useState(false);
  const [gerandoPdf, setGerandoPdf] = useState(false);
  const avisos = useRecado();
  const endereco = `${window.location.origin}/plano/${plano.identificadorPublico}`;

  async function abrirPdf() {
    setGerandoPdf(true);
    try {
      const { url } = await api.prescricoes.pdf(plano.id);
      window.open(url, "_blank", "noopener");
      // Liberação atrasada: revogar antes de a aba nova ler a URL abriria em
      // branco. Um minuto cobre a abertura sem reter o arquivo na memória.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      avisos.avisar(e, "gerar o PDF do plano");
    } finally {
      setGerandoPdf(false);
    }
  }

  async function copiar() {
    try {
      await navigator.clipboard.writeText(endereco);
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <div className="cartao">
      <h3>Entrega ao paciente</h3>

      {/* O impresso vem antes do link: o paciente não tem conta no sistema, e
          sai da consulta com a folha na mão. */}
      <button
        type="button"
        className="botao secundario"
        style={{ width: "100%", justifyContent: "center", marginTop: "0.5rem" }}
        onClick={abrirPdf}
        disabled={gerandoPdf}
      >
        {gerandoPdf ? "Gerando…" : "Plano em PDF"}
      </button>

      {plano.status === "RASCUNHO" ? (
        <>
          <p className="discreto" style={{ marginTop: "0.7rem" }}>
            O plano ainda é um rascunho: quem abrir o link não encontra nada, e o PDF sai marcado
            como rascunho.
          </p>
          <button
            className="botao"
            style={{ width: "100%", justifyContent: "center" }}
            onClick={() => aoExecutar(() => api.prescricoes.publicar(plano.id), "Plano publicado.")}
          >
            Publicar plano
          </button>
        </>
      ) : (
        <>
          <p className="minusculo" style={{ marginTop: "0.4rem", wordBreak: "break-all" }}>
            {endereco}
          </p>
          <div className="linha" style={{ marginTop: "0.5rem" }}>
            <button className="botao secundario pequeno" onClick={copiar}>
              {copiado ? "Copiado!" : "Copiar link"}
            </button>
            <a
              className="botao secundario pequeno"
              href={endereco}
              target="_blank"
              rel="noreferrer"
            >
              Abrir
            </a>
          </div>

          <div className="linha" style={{ marginTop: "0.6rem" }}>
            {plano.status === "ATIVO" && (
              <button
                className="botao secundario pequeno"
                onClick={() =>
                  aoExecutar(() => api.prescricoes.encerrar(plano.id), "Plano encerrado.")
                }
              >
                Encerrar
              </button>
            )}
            <button
              className="botao secundario pequeno"
              onClick={() =>
                aoExecutar(
                  () => api.prescricoes.voltarParaRascunho(plano.id),
                  "Plano voltou a rascunho e saiu do ar.",
                )
              }
            >
              Voltar a rascunho
            </button>
          </div>

          <button
            className="botao perigo pequeno"
            style={{ marginTop: "0.5rem", width: "100%", justifyContent: "center" }}
            onClick={() => {
              if (!confirm("Gerar um novo link invalida o que o paciente já recebeu. Continuar?")) return;
              void aoExecutar(
                () => api.prescricoes.regerarLink(plano.id),
                "Novo link gerado. O anterior deixou de funcionar.",
              );
            }}
          >
            Gerar novo link
          </button>
        </>
      )}
    </div>
  );
}

function arredondar(valor: number) {
  return (Math.round(valor * 100) / 100).toString().replace(".", ",");
}
