import { Fragment, type CSSProperties, type ReactNode } from "react";

import {
  type RichBlock,
  type RichCell,
  type RichDoc,
  type RichParagraph,
  type RichText,
  isEmptyDoc,
} from "./document";
import { fontSizeStyle } from "./editorDocument";

/**
 * Mostra um documento salvo, sem carregar o editor.
 *
 * A tela de leitura aparece em muito mais lugares do que a de escrita — a
 * listagem de anamneses, o plano publicado, a prévia do PDF — e nenhum deles
 * precisa do ProseMirror em memória para desenhar parágrafo e tabela.
 *
 * O tamanho do texto sai daqui em pontos, o mesmo valor que o editor aplica.
 * É isso que faz o corpo 12 continuar corpo 12 depois de reaproveitado: nada
 * neste componente é relativo ao tamanho de quem o contém.
 */
export function RichTextView({
  doc,
  empty = "Sem conteúdo.",
}: {
  doc: RichDoc;
  empty?: string;
}) {
  if (isEmptyDoc(doc)) return <p className="empty">{empty}</p>;

  return (
    <div className="richtext-view">
      {doc.content.map((block, i) => (
        <Fragment key={i}>{renderBlock(block)}</Fragment>
      ))}
    </div>
  );
}

function renderBlock(block: RichBlock): ReactNode {
  switch (block.type) {
    case "paragraph":
      return renderParagraph(block);
    case "bulletList":
    case "orderedList": {
      const List = block.type === "bulletList" ? "ul" : "ol";
      return (
        <List>
          {block.content.map((item, i) => (
            <li key={i}>
              {item.content.map((child, j) => (
                <Fragment key={j}>{renderBlock(child)}</Fragment>
              ))}
            </li>
          ))}
        </List>
      );
    }
    case "table":
      return (
        <table className="richtext-table">
          <tbody>
            {block.content.map((row, i) => (
              <tr key={i}>
                {row.content.map((cell, j) => (
                  <Fragment key={j}>{renderCell(cell)}</Fragment>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      );
  }
}

function renderCell(cell: RichCell): ReactNode {
  const Tag = cell.type === "tableHeader" ? "th" : "td";
  return (
    <Tag colSpan={cell.attrs?.colspan} rowSpan={cell.attrs?.rowspan}>
      {cell.content.map((p, i) => (
        <Fragment key={i}>{renderParagraph(p)}</Fragment>
      ))}
    </Tag>
  );
}

function renderParagraph(paragraph: RichParagraph): ReactNode {
  const style: CSSProperties | undefined = paragraph.attrs?.textAlign
    ? { textAlign: paragraph.attrs.textAlign }
    : undefined;

  // Um parágrafo vazio é uma linha em branco que o autor deixou de propósito.
  if (!paragraph.content?.length) return <p style={style}>{" "}</p>;

  return (
    <p style={style}>
      {paragraph.content.map((text, i) => (
        <Fragment key={i}>{renderText(text)}</Fragment>
      ))}
    </p>
  );
}

function renderText(text: RichText): ReactNode {
  let node: ReactNode = text.text;
  let style: CSSProperties | undefined;

  for (const mark of text.marks ?? []) {
    switch (mark.type) {
      case "bold":
        node = <strong>{node}</strong>;
        break;
      case "italic":
        node = <em>{node}</em>;
        break;
      case "underline":
        node = <u>{node}</u>;
        break;
      case "strike":
        node = <s>{node}</s>;
        break;
      case "textStyle":
        style = { fontSize: fontSizeStyle(mark.attrs.fontSize) };
        break;
    }
  }

  return style ? <span style={style}>{node}</span> : node;
}
