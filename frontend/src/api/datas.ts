/**
 * Datas no fuso do usuário.
 *
 * `toISOString()` converte para UTC antes de formatar. No Brasil (UTC−3), abrir
 * o sistema depois das 21h faria "hoje" virar o dia seguinte — a agenda abriria
 * amanhã, e uma avaliação registrada à noite ganharia data futura, que o
 * servidor recusa. Estas funções formatam a partir dos componentes locais.
 */

export function paraIso(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, "0");
  const dia = String(data.getDate()).padStart(2, "0");
  return `${ano}-${mes}-${dia}`;
}

export function hojeIso(): string {
  return paraIso(new Date());
}

export function primeiroDiaDoMesIso(): string {
  const hoje = new Date();
  return paraIso(new Date(hoje.getFullYear(), hoje.getMonth(), 1));
}

export function ultimoDiaDoMesIso(): string {
  const hoje = new Date();
  return paraIso(new Date(hoje.getFullYear(), hoje.getMonth() + 1, 0));
}

/** Soma dias a uma data ISO, devolvendo outra data ISO. */
export function somarDias(iso: string, dias: number): string {
  const [ano, mes, dia] = iso.split("-").map(Number);
  const data = new Date(ano!, mes! - 1, dia! + dias);
  return paraIso(data);
}

export function formatarBr(iso?: string): string {
  if (!iso) return "—";
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

/**
 * Segunda-feira da semana em que a data cai.
 *
 * `getDay()` devolve 0 para domingo, e a semana de trabalho de um consultório
 * começa na segunda — sem o ajuste, a semana de um domingo mostraria os seis
 * dias seguintes em vez dos seis anteriores.
 */
export function inicioDaSemana(iso: string): string {
  const [ano, mes, dia] = iso.split("-").map(Number);
  const data = new Date(ano!, mes! - 1, dia!);
  const diaDaSemana = (data.getDay() + 6) % 7;
  return somarDias(iso, -diaDaSemana);
}

/** Nome do dia da semana, ex.: "seg". */
export function diaAbreviado(iso: string): string {
  const [ano, mes, dia] = iso.split("-").map(Number);
  return new Date(ano!, mes! - 1, dia!)
    .toLocaleDateString("pt-BR", { weekday: "short" })
    .replace(".", "");
}
