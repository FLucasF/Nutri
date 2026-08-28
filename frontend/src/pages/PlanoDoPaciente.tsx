import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ErroApi, api } from "../api/client";
import type { ItemPublico, PlanoPublico } from "../api/types";

/**
 * Plano como o paciente o recebe.
 *
 * Nenhuma credencial é exigida: quem tem o link, vê. Por isso a página só
 * exibe o que o servidor entrega no contrato público — que não tem campo para
 * anotação interna do consultório.
 *
 * A leitura é a régua do dia: as refeições penduram numa linha do tempo, com o
 * horário real como marcador, e o vão entre o café e o almoço aparece como vão
 * de verdade. Dentro de cada item a medida caseira é o texto maior — o
 * paciente já sabe o que é arroz; o que ele não sabe é quanto.
 */
export default function PlanoDoPaciente() {
  const { identificador } = useParams();
  const [plano, setPlano] = useState<PlanoPublico | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!identificador) return;
    api
      .planoPublico(identificador)
      .then(setPlano)
      .catch((e) =>
        setErro(
          e instanceof ErroApi
            ? e.message
            : "Não foi possível abrir o plano. Verifique sua conexão.",
        ),
      )
      .finally(() => setCarregando(false));
  }, [identificador]);

  useEffect(() => {
    if (plano?.titulo) {
      document.title = `${plano.titulo} — NutriPlan`;
    }
  }, [plano]);

  if (carregando) {
    return (
      <div className="pagina-paciente">
        <p className="carregando">Carregando seu plano…</p>
      </div>
    );
  }

  if (erro || !plano) {
    return (
      <div className="pagina-paciente">
        <div className="plano-paciente" style={{ paddingTop: "3rem" }}>
          <div className="cartao" style={{ textAlign: "center", padding: "2.5rem 1.5rem" }}>
            <h1 style={{ fontSize: "1.5rem" }}>Plano não encontrado</h1>
            <p className="discreto" style={{ marginTop: "0.6rem" }}>
              {erro ?? "Confira o link que você recebeu do seu nutricionista."}
            </p>
          </div>
        </div>
      </div>
    );
  }

  // A cor do consultório personaliza a página do paciente. O CSS a recebe como
  // --tema-conta e decide o valor final, porque no tema escuro ela precisa ser
  // clareada para não sumir contra o fundo.
  const corDoConsultorio = plano.corPrimaria || undefined;

  return (
    <div
      className="pagina-paciente"
      style={
        corDoConsultorio
          ? ({ ["--tema-conta" as string]: corDoConsultorio } as React.CSSProperties)
          : undefined
      }
    >
      <div className="plano-paciente">
        <header className="capa-plano">
          {plano.consultorioNome && <span className="eyebrow">{plano.consultorioNome}</span>}
          <h1>{plano.titulo}</h1>
          <p>
            {plano.pacienteNome ? `Olá, ${plano.pacienteNome}. ` : ""}
            {plano.nutricionistaNome
              ? `Plano elaborado por ${plano.nutricionistaNome}${plano.nutricionistaCrn ? ` · ${plano.nutricionistaCrn}` : ""}.`
              : ""}
          </p>
          {plano.vigenciaInicio && (
            <p className="vigencia">
              Vigência: {formatarData(plano.vigenciaInicio)}
              {plano.vigenciaFim ? ` até ${formatarData(plano.vigenciaFim)}` : " em diante"}
            </p>
          )}
        </header>

        {plano.encerrado && (
          <div className="aviso atencao">
            <strong>Este plano foi encerrado.</strong> Ele fica disponível para consulta, mas não é
            mais o plano vigente. Procure seu nutricionista para receber o plano atual.
          </div>
        )}

        {!plano.encerrado && !plano.vigente && (
          <div className="aviso atencao">
            Este plano está fora do período de vigência definido pelo seu nutricionista.
          </div>
        )}

        {plano.orientacoes && (
          <div className="cartao">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.5rem" }}>Orientações gerais</h2>
            <p style={{ margin: 0, whiteSpace: "pre-line", fontSize: "0.92rem" }}>
              {plano.orientacoes}
            </p>
          </div>
        )}

        {plano.orientacoesAnexadas?.length > 0 && (
          <div className="cartao">
            {plano.orientacoesAnexadas.map((o, i) => (
              <div className="orientacao-anexada" key={o.id ?? i} style={i === 0 ? { marginTop: 0 } : undefined}>
                <h3>{o.titulo}</h3>
                <p>{o.corpo}</p>
                {/* Rota pública: o endereço vem pronto na resposta e a tag
                    busca sozinha, sem credencial nenhuma. */}
                {/* Sem `loading="lazy"`: são uma ou duas figuras pequenas, e o
                    paciente costuma imprimir esta página. Imagem adiada é
                    imagem que arrisca sair em branco no papel. */}
                {o.imagem && (
                  <img className="figura-orientacao" src={o.imagem} alt={o.titulo} />
                )}
              </div>
            ))}
          </div>
        )}

        <div className="regua-dia">
          {plano.refeicoes.map((refeicao, indice) => (
            <div
              className="regua-item"
              key={indice}
              style={{ ["--ordem" as string]: indice } as React.CSSProperties}
            >
              {refeicao.horario ? (
                <time className="regua-hora" dateTime={refeicao.horario}>
                  {refeicao.horario.slice(0, 5)}
                </time>
              ) : (
                // Refeição sem horário prescrito não ganha uma hora inventada.
                <span className="regua-hora sem-hora">livre</span>
              )}

              <div className="regua-corpo">
                <section className="refeicao-paciente">
                  <h2>{refeicao.nome}</h2>
                  {refeicao.observacao && (
                    <p className="nota-refeicao">{refeicao.observacao}</p>
                  )}
                  {refeicao.itens.map((item, i) => (
                    <Item item={item} key={i} />
                  ))}
                </section>
              </div>
            </div>
          ))}
        </div>

        {plano.metodo !== "QUALITATIVO" && plano.resumo.energiaKcal !== undefined && (
          <div className="cartao">
            <h2 style={{ fontSize: "1.05rem", marginBottom: "0.9rem" }}>Resumo do dia</h2>
            <div className="resumo-dia">
              <Resumo rotulo="Energia" valor={plano.resumo.energiaKcal} unidade="kcal" />
              <Resumo rotulo="Proteínas" valor={plano.resumo.proteinaG} unidade="g" />
              <Resumo rotulo="Carboidratos" valor={plano.resumo.carboidratoG} unidade="g" />
              <Resumo rotulo="Gorduras" valor={plano.resumo.lipideosG} unidade="g" />
            </div>
            <p className="minusculo" style={{ marginTop: "0.8rem", marginBottom: 0 }}>
              Valores estimados a partir das tabelas de composição de alimentos.
            </p>
          </div>
        )}

        <footer className="minusculo" style={{ textAlign: "center", paddingTop: "0.5rem" }}>
          Em caso de dúvida, fale com seu nutricionista.
          {plano.nutricionistaNome ? ` — ${plano.nutricionistaNome}` : ""}
        </footer>
      </div>
    </div>
  );
}

function Item({ item }: { item: ItemPublico }) {
  return (
    <div className="item-paciente">
      <span className="nome-alimento">{item.descricao}</span>
      <span className="porcao">
        {item.porcao}
        {/* A grama só acompanha quando a porção é uma medida caseira. Repetir
            "100 g / 100 g" não informaria nada. */}
        {item.pesoGramas !== undefined && !ehPesoEmGramas(item.porcao) && (
          <span className="peso">{formatarPeso(item.pesoGramas)}</span>
        )}
      </span>
      {item.observacao && (
        <p className="discreto" style={{ margin: "0.3rem 0 0", fontSize: "0.85rem" }}>
          {item.observacao}
        </p>
      )}
      {item.substituicoes.length > 0 && (
        <div className="substituicoes">
          <span className="titulo-sub">Você pode substituir por</span>
          <ul>
            {item.substituicoes.map((s, j) => (
              <li key={j}>
                {s.descricao} — {s.porcao}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Resumo({ rotulo, valor, unidade }: { rotulo: string; valor?: number; unidade: string }) {
  return (
    <div>
      <span className="rotulo">{rotulo}</span>
      <div className="valor">
        {valor === undefined || valor === null ? "—" : `${arredondar(valor)} ${unidade}`}
      </div>
    </div>
  );
}

/** A porção já é o próprio peso quando não há medida caseira, ex.: "100 g". */
function ehPesoEmGramas(porcao: string) {
  return /^\s*[\d.,]+\s*g\s*$/i.test(porcao);
}

function formatarPeso(gramas: number) {
  const numero = Number.isInteger(gramas) ? gramas : Number(gramas.toFixed(1));
  return `${numero.toLocaleString("pt-BR")} g`;
}

/**
 * Arredonda preservando a ordem de grandeza.
 *
 * Arredondar 0,23 g para "0 g" diria ao paciente que a refeição não tem
 * gordura — que é afirmação diferente de "tem pouca". Valores pequenos ganham
 * casas decimais; os grandes ficam inteiros, que é como se lê uma caloria.
 */
function arredondar(valor: number): string {
  if (valor >= 100) return Math.round(valor).toLocaleString("pt-BR");
  if (valor >= 10) return valor.toFixed(1).replace(".", ",");
  return valor.toFixed(2).replace(".", ",");
}

function formatarData(iso: string) {
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}
