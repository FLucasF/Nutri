/**
 * Tradução entre o documento guardado e o documento que o editor manipula.
 *
 * São quase o mesmo formato, com uma diferença deliberada: o TipTap guarda o
 * tamanho da fonte como string CSS ("12pt") e aqui guardamos o número (12). O
 * número é o que dá para validar contra um conjunto fechado dos dois lados da
 * API — e é o que o gerador de PDF consome sem ter que interpretar CSS.
 *
 * A conversão é curta de propósito. Se um dia ela crescer, é sinal de que o
 * formato do editor virou o formato de armazenamento sem ninguém decidir isso.
 */
import {
  type FontSize,
  type RichDoc,
  parseFontSize,
  parseRichDoc,
} from "./document";

type Json = { [key: string]: unknown };

/** Documento guardado → JSON do ProseMirror, com o tamanho em CSS. */
export function toEditorDoc(doc: RichDoc): Json {
  return mapNode(doc as unknown as Json, (size) => `${size}pt`);
}

/** JSON do ProseMirror → documento guardado, validando tudo na volta. */
export function fromEditorDoc(value: unknown): RichDoc | null {
  return parseRichDoc(value);
}

/** O CSS que o editor e a tela de leitura aplicam ao texto. */
export function fontSizeStyle(size: FontSize): string {
  return `${size}pt`;
}

function mapNode(node: Json, writeSize: (size: FontSize) => string): Json {
  const out: Json = { ...node };

  if (Array.isArray(node.marks)) {
    out.marks = node.marks.map((mark) => {
      if (
        typeof mark === "object" &&
        mark !== null &&
        (mark as Json).type === "textStyle" &&
        typeof (mark as Json).attrs === "object"
      ) {
        const attrs = (mark as Json).attrs as Json;
        const size = parseFontSize(attrs.fontSize);
        if (size !== null) {
          return { ...(mark as Json), attrs: { ...attrs, fontSize: writeSize(size) } };
        }
      }
      return mark;
    });
  }

  if (Array.isArray(node.content)) {
    out.content = node.content.map((child) =>
      typeof child === "object" && child !== null ? mapNode(child as Json, writeSize) : child,
    );
  }

  return out;
}
