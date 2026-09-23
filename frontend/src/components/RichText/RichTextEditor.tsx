import { useEffect, useMemo, useState } from "react";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TextAlign from "@tiptap/extension-text-align";
import { FontSize, TextStyle } from "@tiptap/extension-text-style";
import { Table, TableCell, TableHeader, TableRow } from "@tiptap/extension-table";

import {
  ALIGNMENTS,
  FONT_SIZES,
  FONT_SIZE_DEFAULT,
  type Alignment,
  type FontSize as Size,
  type RichDoc,
  emptyDoc,
} from "./document";
import { fromEditorDoc, toEditorDoc } from "./editorDocument";

/**
 * O editor de texto do sistema.
 *
 * O conjunto de recursos é o que o cliente pediu, e nada além: negrito,
 * itálico, sublinhado, tachado, tamanho de fonte, marcadores, numeração,
 * alinhamento e tabela com mesclagem. Cabeçalho, citação, código e linha
 * horizontal estão desligados de propósito — o modelo em `document.ts` não
 * descreve nenhum deles, então deixá-los ligados criaria conteúdo que a
 * validação recusaria na hora de salvar.
 */
export function RichTextEditor({
  value,
  onChange,
  onlyRead = false,
  minHeight = "12rem",
  label,
}: {
  value: RichDoc;
  onChange: (doc: RichDoc) => void;
  onlyRead?: boolean;
  minHeight?: string;
  label: string;
}) {
  const extensions = useMemo(
    () => [
      StarterKit.configure({
        blockquote: false,
        code: false,
        codeBlock: false,
        hardBreak: false,
        heading: false,
        horizontalRule: false,
        link: false,
        trailingNode: false,
      }),
      TextStyle,
      FontSize,
      TextAlign.configure({ types: ["paragraph"], alignments: [...ALIGNMENTS] }),
      Table.configure({ resizable: true }),
      TableRow,
      TableHeader,
      TableCell,
    ],
    [],
  );

  const [outsideModel, setOutsideModel] = useState(false);

  const editor = useEditor({
    extensions,
    editable: !onlyRead,
    content: toEditorDoc(value),
    editorProps: {
      attributes: { class: "richtext-area", "aria-label": label, role: "textbox" },
    },
    onUpdate: ({ editor: e }) => {
      const doc = fromEditorDoc(e.getJSON());
      // `null` só acontece se o editor produzir algo que o modelo não descreve.
      // Com as extensões acima isso não tem caminho conhecido — mas se abrir um,
      // quem está digitando precisa saber na hora. Engolir a alteração em
      // silêncio faria o texto parar de salvar sem nenhum sinal na tela.
      if (doc) {
        setOutsideModel(false);
        onChange(doc);
      } else {
        setOutsideModel(true);
      }
    },
  });

  useEffect(() => {
    if (editor) editor.setEditable(!onlyRead);
  }, [editor, onlyRead]);

  if (!editor) return <div className="richtext" style={{ minHeight }} />;

  return (
    <div className="richtext">
      {!onlyRead && <Toolbar editor={editor} />}
      {outsideModel && (
        <p className="richtext-alerta" role="alert">
          A última alteração usa um recurso que o sistema não guarda, então ela não foi
          registrada. Desfaça com Ctrl+Z e refaça de outra forma.
        </p>
      )}
      <EditorContent editor={editor} style={{ minHeight }} />
    </div>
  );
}

// ------------------------------------------------------------------ barra

function Toolbar({ editor }: { editor: Editor }) {
  const inTable = editor.isActive("table");

  return (
    <div className="richtext-bar">
      <div className="richtext-group" role="group" aria-label="Formatação">
        <Mark editor={editor} mark="bold" label="Negrito" shown="N" />
        <Mark editor={editor} mark="italic" label="Itálico" shown="I" />
        <Mark editor={editor} mark="underline" label="Sublinhado" shown="S" />
        <Mark editor={editor} mark="strike" label="Tachado" shown="T" />
      </div>

      <div className="richtext-group">
        <label className="visually-hidden" htmlFor="richtext-size">
          Tamanho da fonte
        </label>
        <select
          id="richtext-size"
          className="richtext-size"
          value={currentSize(editor)}
          onChange={(e) => {
            const size = Number(e.target.value) as Size;
            editor.chain().focus().setFontSize(`${size}pt`).run();
          }}
        >
          {FONT_SIZES.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </div>

      <div className="richtext-group" role="group" aria-label="Listas">
        <Command
          editor={editor}
          label="Marcadores"
          shown="• —"
          active={editor.isActive("bulletList")}
          run={() => editor.chain().focus().toggleBulletList().run()}
        />
        <Command
          editor={editor}
          label="Numeração"
          shown="1. —"
          active={editor.isActive("orderedList")}
          run={() => editor.chain().focus().toggleOrderedList().run()}
        />
      </div>

      <div className="richtext-group" role="group" aria-label="Alinhamento">
        <Align editor={editor} align="left" label="Alinhar à esquerda" />
        <Align editor={editor} align="center" label="Centralizar" />
        <Align editor={editor} align="right" label="Alinhar à direita" />
        <Align editor={editor} align="justify" label="Justificar" />
      </div>

      <div className="richtext-group" role="group" aria-label="Tabela">
        {inTable ? (
          <>
            <Command
              editor={editor}
              label="Inserir linha"
              shown="+ linha"
              run={() => editor.chain().focus().addRowAfter().run()}
            />
            <Command
              editor={editor}
              label="Inserir coluna"
              shown="+ coluna"
              run={() => editor.chain().focus().addColumnAfter().run()}
            />
            <Command
              editor={editor}
              label="Mesclar ou dividir células"
              shown="mesclar"
              run={() => editor.chain().focus().mergeOrSplit().run()}
            />
            <Command
              editor={editor}
              label="Remover linha"
              shown="− linha"
              run={() => editor.chain().focus().deleteRow().run()}
            />
            <Command
              editor={editor}
              label="Remover coluna"
              shown="− coluna"
              run={() => editor.chain().focus().deleteColumn().run()}
            />
            <Command
              editor={editor}
              label="Remover tabela"
              shown="remover"
              run={() => editor.chain().focus().deleteTable().run()}
            />
          </>
        ) : (
          <Command
            editor={editor}
            label="Inserir tabela"
            shown="tabela"
            run={() =>
              editor
                .chain()
                .focus()
                .insertTable({ rows: 3, cols: 3, withHeaderRow: true })
                .run()
            }
          />
        )}
      </div>
    </div>
  );
}

function Mark({
  editor,
  mark,
  label,
  shown,
}: {
  editor: Editor;
  mark: "bold" | "italic" | "underline" | "strike";
  label: string;
  shown: string;
}) {
  return (
    <Command
      editor={editor}
      label={label}
      shown={shown}
      active={editor.isActive(mark)}
      run={() => {
        const chain = editor.chain().focus();
        if (mark === "bold") chain.toggleBold().run();
        else if (mark === "italic") chain.toggleItalic().run();
        else if (mark === "underline") chain.toggleUnderline().run();
        else chain.toggleStrike().run();
      }}
    />
  );
}

function Align({
  editor,
  align,
  label,
}: {
  editor: Editor;
  align: Alignment;
  label: string;
}) {
  const shown = { left: "⇤", center: "↔", right: "⇥", justify: "≡" }[align];
  return (
    <Command
      editor={editor}
      label={label}
      shown={shown}
      active={editor.isActive({ textAlign: align })}
      run={() => editor.chain().focus().setTextAlign(align).run()}
    />
  );
}

function Command({
  label,
  shown,
  active = false,
  run,
}: {
  editor: Editor;
  label: string;
  shown: string;
  active?: boolean;
  run: () => void;
}) {
  return (
    <button
      type="button"
      className={`richtext-button${active ? " ativo" : ""}`}
      aria-label={label}
      aria-pressed={active}
      title={label}
      onMouseDown={(e) => e.preventDefault()}
      onClick={run}
    >
      {shown}
    </button>
  );
}

/** O tamanho da seleção, ou o padrão quando a seleção não declara um. */
function currentSize(editor: Editor): number {
  const raw: unknown = editor.getAttributes("textStyle").fontSize;
  if (typeof raw === "string") {
    const n = Number.parseFloat(raw.replace("pt", ""));
    if ((FONT_SIZES as readonly number[]).includes(n)) return n;
  }
  return FONT_SIZE_DEFAULT;
}

export { emptyDoc };
