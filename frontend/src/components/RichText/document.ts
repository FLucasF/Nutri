/**
 * O documento de texto rico, como modelo fechado.
 *
 * Guardar HTML livre seria mais curto e traria de volta a dor que o cliente
 * descreve duas vezes: texto salvo em corpo 12 volta maior dentro do cardápio.
 * Isso acontece quando o tamanho é relativo — um `em` herda a escala do lugar
 * onde o texto foi colado, então o mesmo trecho muda de tamanho conforme a
 * tela. Aqui o tamanho é absoluto em pontos, vem de um conjunto fechado, e o
 * mesmo valor é usado no editor, na tela e no PDF. Doze é doze em todo lugar.
 *
 * O formato é o do ProseMirror, para o editor não precisar de tradução. O que
 * este arquivo acrescenta é o contrato: quais nós e quais marcas existem. O que
 * não estiver descrito aqui é recusado na entrada, não normalizado em silêncio
 * — texto clínico que chega torto e é "consertado" sozinho é pior do que texto
 * recusado, porque ninguém fica sabendo.
 */

/** Tamanhos em pontos, como no Word. Fechado de propósito. */
export const FONT_SIZES = [8, 9, 10, 11, 12, 14, 16, 18, 24] as const;
export type FontSize = (typeof FONT_SIZES)[number];

/** O tamanho do texto que não declara tamanho. */
export const FONT_SIZE_DEFAULT: FontSize = 11;

export const ALIGNMENTS = ["left", "center", "right", "justify"] as const;
export type Alignment = (typeof ALIGNMENTS)[number];

export type RichMark =
  | { type: "bold" }
  | { type: "italic" }
  | { type: "underline" }
  | { type: "strike" }
  | { type: "textStyle"; attrs: { fontSize: FontSize } };

export type RichText = {
  type: "text";
  text: string;
  marks?: RichMark[];
};

export type RichParagraph = {
  type: "paragraph";
  attrs?: { textAlign?: Alignment };
  content?: RichText[];
};

/**
 * Um item de lista carrega blocos — inclusive outra lista.
 *
 * O aninhamento não é enfeite: é o Tab dentro do marcador, que é o
 * comportamento do Word que o cliente pediu. E o item aceita bloco em vez
 * de só parágrafo porque é isso que o editor sabe produzir ali dentro —
 * um modelo mais estreito do que o editor faz o texto parar de salvar em
 * silêncio, que é pior do que aceitar uma tabela dentro de um marcador.
 */
export type RichListItem = {
  type: "listItem";
  content: RichBlock[];
};

export type RichList = {
  type: "bulletList" | "orderedList";
  content: RichListItem[];
};

export type RichCell = {
  type: "tableCell" | "tableHeader";
  /** `colspan` e `rowspan` são o que sustenta a mesclagem de células. */
  attrs?: { colspan?: number; rowspan?: number; colwidth?: number[] | null };
  content: RichParagraph[];
};

export type RichRow = {
  type: "tableRow";
  content: RichCell[];
};

export type RichTable = {
  type: "table";
  content: RichRow[];
};

export type RichBlock = RichParagraph | RichList | RichTable;

export type RichDoc = {
  type: "doc";
  content: RichBlock[];
};

/** Um documento vazio sempre tem um parágrafo: o cursor precisa de onde pousar. */
export function emptyDoc(): RichDoc {
  return { type: "doc", content: [{ type: "paragraph" }] };
}

// ------------------------------------------------------------------ validação

/**
 * A fronteira sem tipo.
 *
 * O que vem da API é `unknown` em tempo de execução, por mais que o tipo diga
 * outra coisa. Aqui é onde `unknown` vira `RichDoc` — ou não vira, e a tela
 * mostra o texto puro em vez de quebrar.
 */
export function parseRichDoc(value: unknown): RichDoc | null {
  if (typeof value === "string") {
    if (!value.trim()) return emptyDoc();
    let decoded: unknown;
    try {
      decoded = JSON.parse(value) as unknown;
    } catch {
      // Conteúdo antigo, gravado como texto puro antes deste formato existir.
      return fromPlainText(value);
    }
    // "12" é JSON válido e não é documento. Texto que já era texto continua texto.
    return parseRichDoc(decoded) ?? fromPlainText(value);
  }
  if (!isObject(value) || value.type !== "doc") return null;
  const content = readArray(value.content, readBlock);
  if (content === null) return null;
  return { type: "doc", content: content.length ? content : [{ type: "paragraph" }] };
}

/** Envelopa texto puro em parágrafos, preservando as quebras de linha. */
export function fromPlainText(text: string): RichDoc {
  const paragraphs = text.split(/\r?\n/).map<RichParagraph>((line) =>
    line ? { type: "paragraph", content: [{ type: "text", text: line }] } : { type: "paragraph" },
  );
  return { type: "doc", content: paragraphs.length ? paragraphs : [{ type: "paragraph" }] };
}

/** O texto sem formatação, para busca, resumo e rótulo curto. */
export function toPlainText(doc: RichDoc): string {
  const lines: string[] = [];

  const paragraphText = (p: RichParagraph) => (p.content ?? []).map((t) => t.text).join("");

  const walkBlock = (block: RichBlock) => {
    if (block.type === "paragraph") {
      lines.push(paragraphText(block));
      return;
    }
    if (block.type === "table") {
      for (const row of block.content) {
        lines.push(
          row.content
            .map((cell) => cell.content.map(paragraphText).join(" "))
            .join("\t"),
        );
      }
      return;
    }
    for (const item of block.content) {
      for (const child of item.content) walkBlock(child);
    }
  };

  for (const block of doc.content) walkBlock(block);
  return lines.join("\n").trim();
}

/** Diz se o documento tem algum texto — um doc "vazio" tem um parágrafo em branco. */
export function isEmptyDoc(doc: RichDoc): boolean {
  return toPlainText(doc).length === 0;
}

// ------------------------------------------------------------------- internos

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function readArray<T>(value: unknown, read: (item: unknown) => T | null): T[] | null {
  if (value === undefined) return [];
  if (!Array.isArray(value)) return null;
  const out: T[] = [];
  for (const item of value) {
    const parsed = read(item);
    if (parsed === null) return null;
    out.push(parsed);
  }
  return out;
}

function readBlock(value: unknown): RichBlock | null {
  if (!isObject(value)) return null;
  switch (value.type) {
    case "paragraph":
      return readParagraph(value);
    case "bulletList":
    case "orderedList": {
      const content = readArray(value.content, readListItem);
      if (content === null) return null;
      return { type: value.type, content };
    }
    case "table": {
      const content = readArray(value.content, readRow);
      if (content === null) return null;
      return { type: "table", content };
    }
    default:
      return null;
  }
}

function readParagraph(value: Record<string, unknown>): RichParagraph | null {
  const content = readArray(value.content, readText);
  if (content === null) return null;
  const paragraph: RichParagraph = { type: "paragraph" };
  const align = isObject(value.attrs) ? value.attrs.textAlign : undefined;
  if (typeof align === "string" && (ALIGNMENTS as readonly string[]).includes(align)) {
    paragraph.attrs = { textAlign: align as Alignment };
  }
  if (content.length) paragraph.content = content;
  return paragraph;
}

function readListItem(value: unknown): RichListItem | null {
  if (!isObject(value) || value.type !== "listItem") return null;
  const content = readArray(value.content, readBlock);
  if (content === null) return null;
  return { type: "listItem", content: content.length ? content : [{ type: "paragraph" }] };
}

function readRow(value: unknown): RichRow | null {
  if (!isObject(value) || value.type !== "tableRow") return null;
  const content = readArray(value.content, readCell);
  if (content === null) return null;
  return { type: "tableRow", content };
}

function readCell(value: unknown): RichCell | null {
  if (!isObject(value)) return null;
  if (value.type !== "tableCell" && value.type !== "tableHeader") return null;
  const content = readArray(value.content, (item) =>
    isObject(item) && item.type === "paragraph" ? readParagraph(item) : null,
  );
  if (content === null) return null;
  const cell: RichCell = {
    type: value.type,
    content: content.length ? content : [{ type: "paragraph" }],
  };
  if (isObject(value.attrs)) {
    const attrs: NonNullable<RichCell["attrs"]> = {};
    if (isSpan(value.attrs.colspan)) attrs.colspan = value.attrs.colspan;
    if (isSpan(value.attrs.rowspan)) attrs.rowspan = value.attrs.rowspan;
    if (Array.isArray(value.attrs.colwidth)) {
      const widths = value.attrs.colwidth.filter((w): w is number => typeof w === "number");
      if (widths.length) attrs.colwidth = widths;
    }
    if (Object.keys(attrs).length) cell.attrs = attrs;
  }
  return cell;
}

function isSpan(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 1 && value <= 50;
}

function readText(value: unknown): RichText | null {
  if (!isObject(value) || value.type !== "text" || typeof value.text !== "string") return null;
  if (value.marks !== undefined && !Array.isArray(value.marks)) return null;
  const marks: RichMark[] = [];
  for (const raw of (value.marks as unknown[]) ?? []) {
    const mark = readMark(raw);
    if (mark === null) return null;
    // `undefined` é a marca que existe mas não diz nada — um textStyle sem
    // tamanho, que é como o ProseMirror deixa a marca depois de limpá-la.
    // Ela sai do documento sem virar outra coisa.
    if (mark !== undefined) marks.push(mark);
  }
  const text: RichText = { type: "text", text: value.text };
  if (marks.length) text.marks = marks;
  return text;
}

/** `null` recusa o documento; `undefined` descarta só esta marca. */
function readMark(value: unknown): RichMark | null | undefined {
  if (!isObject(value)) return null;
  switch (value.type) {
    case "bold":
    case "italic":
    case "underline":
    case "strike":
      return { type: value.type };
    case "textStyle": {
      const size = isObject(value.attrs) ? value.attrs.fontSize : undefined;
      const parsed = parseFontSize(size);
      return parsed === null ? undefined : { type: "textStyle", attrs: { fontSize: parsed } };
    }
    default:
      return null;
  }
}

/** Aceita 12, "12" e "12pt", e recusa qualquer coisa fora do conjunto. */
export function parseFontSize(value: unknown): FontSize | null {
  const n =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number.parseFloat(value.replace("pt", "").trim())
        : Number.NaN;
  return (FONT_SIZES as readonly number[]).includes(n) ? (n as FontSize) : null;
}
