/**
 * O avatar do paciente, com as iniciais.
 *
 * O cliente pediu inicialmente rosa para mulher e azul para homem, e depois
 * explicou o que queria de verdade: "ter uma estimativa só ou para procurar tal
 * paciente sem ter que olhar diretamente para o nome". Ele mesmo concluiu que a
 * regra por sexo é dispensável.
 *
 * Duas cores por sexo dariam dois grupos numa lista de centenas — o que não
 * ajuda a achar ninguém. Quem resolve isso é a TAG, que ele cria quantas
 * quiser. Aqui a cor sai do próprio nome, então cada paciente tem sempre a
 * mesma e a varredura funciona por forma, não por categoria.
 */

/** A inicial do primeiro nome e a do último sobrenome — "Vitória de Araújo Ferreira" → VF. */
export function initialsOf(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 2 || /^[A-ZÀ-Ý]/.test(part));
  const first = parts[0];
  const last = parts.length > 1 ? parts[parts.length - 1] : undefined;
  if (!first) return "?";
  const a = first[0] ?? "";
  const b = last?.[0] ?? "";
  return (a + b).toUpperCase();
}

/**
 * Um matiz estável para o nome.
 *
 * Soma simples dos códigos das letras. Não precisa ser criptográfica: precisa
 * ser a mesma toda vez, para o olho aprender a cor de cada paciente.
 */
function hueOf(name: string): number {
  let sum = 0;
  for (let i = 0; i < name.length; i++) sum = (sum + name.charCodeAt(i) * (i + 1)) % 360;
  return sum;
}

export function Avatar({ name, size = 34 }: { name: string; size?: number }) {
  const hue = hueOf(name);
  return (
    <span
      className="avatar"
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        fontSize: size * 0.38,
        background: `oklch(0.92 0.045 ${hue})`,
        color: `oklch(0.42 0.09 ${hue})`,
      }}
    >
      {initialsOf(name)}
    </span>
  );
}
