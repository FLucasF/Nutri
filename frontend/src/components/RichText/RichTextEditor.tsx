import { useEffect, useMemo, useState, type CSSProperties } from "react";
import { EditorContent, useEditor, type Editor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import TextAlign from "@tiptap/extension-text-align";
import { FontSize, TextStyle } from "@tiptap/extension-text-style";
import { Table, TableCell, TableHeader, TableRow } from "@tiptap/extension-table";
import {
  ALargeSmall,
  AlignCenter,
  AlignJustify,
  AlignLeft,
  AlignRight,
  BetweenHorizontalStart,
  BetweenVerticalStart,
  Bold,
  Grid2x2X,
  Italic,
  List,
  ListOrdered,
  Strikethrough,
  Table as TableIcon,
  TableCellsMerge,
  Trash2,
  Underline,
  type LucideIcon,
} from "lucide-react";

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

  // A altura mínima é dado da tela que chama (uma observação de item é menor
  // que o modo de preparo), então viaja como variável para a moldura; o CSS
  // aplica na área de texto, e o mesmo valor serve à moldura de espera.
  const frame = richTextFrameStyle(minHeight);

  if (!editor) {
    return (
      <div className="richtext" style={frame}>
        {!onlyRead && <div className="richtext-bar" aria-hidden="true" />}
      </div>
    );
  }

  return (
    <div className="richtext" style={frame}>
      {!onlyRead && <Toolbar editor={editor} />}
      {outsideModel && (
        <p className="richtext-alerta" role="alert">
          A última alteração usa um recurso que o sistema não guarda, então ela não foi
          registrada. Desfaça com Ctrl+Z e refaça de outra forma.
        </p>
      )}
      <EditorContent editor={editor} />
    </div>
  );
}

/**
 * A moldura recebe a altura mínima como variável, lida por `.richtext-area`.
 * LazyEditor repete esta linha em vez de importá-la: importar um valor daqui
 * traria o ProseMirror para o pacote principal.
 */
function richTextFrameStyle(minHeight: string): CSSProperties {
  return { "--richtext-min-h": minHeight } as CSSProperties;
}

// ------------------------------------------------------------------ barra

function Toolbar({ editor }: { editor: Editor }) {
  const inTable = editor.isActive("table");

  return (
    <div className="richtext-bar">
      <div className="richtext-group" role="group" aria-label="Formatação">
        <Mark editor={editor} mark="bold" label="Negrito" icon={Bold} />
        <Mark editor={editor} mark="italic" label="Itálico" icon={Italic} />
        <Mark editor={editor} mark="underline" label="Sublinhado" icon={Underline} />
        <Mark editor={editor} mark="strike" label="Tachado" icon={Strikethrough} />
      </div>

      <div className="richtext-group richtext-group-size">
        <ALargeSmall aria-hidden="true" className="richtext-size-icon" />
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
          icon={List}
          active={editor.isActive("bulletList")}
          run={() => editor.chain().focus().toggleBulletList().run()}
        />
        <Command
          editor={editor}
          label="Numeração"
          icon={ListOrdered}
          active={editor.isActive("orderedList")}
          run={() => editor.chain().focus().toggleOrderedList().run()}
        />
      </div>

      <div className="richtext-group" role="group" aria-label="Alinhamento">
        <Align editor={editor} align="left" label="Alinhar à esquerda" icon={AlignLeft} />
        <Align editor={editor} align="center" label="Centralizar" icon={AlignCenter} />
        <Align editor={editor} align="right" label="Alinhar à direita" icon={AlignRight} />
        <Align editor={editor} align="justify" label="Justificar" icon={AlignJustify} />
      </div>

      <div className="richtext-group" role="group" aria-label="Tabela">
        {inTable ? (
          <>
            <Command
              editor={editor}
              label="Inserir linha"
              icon={BetweenHorizontalStart}
              run={() => editor.chain().focus().addRowAfter().run()}
            />
            <Command
              editor={editor}
              label="Inserir coluna"
              icon={BetweenVerticalStart}
              run={() => editor.chain().focus().addColumnAfter().run()}
            />
            <Command
              editor={editor}
              label="Mesclar ou dividir células"
              icon={TableCellsMerge}
              run={() => editor.chain().focus().mergeOrSplit().run()}
            />
            {/* Uma lixeira sozinha não diz o que remove; a palavra ao lado diz. */}
            <Command
              editor={editor}
              label="Remover linha"
              icon={Trash2}
              shown="linha"
              tone="perigo"
              run={() => editor.chain().focus().deleteRow().run()}
            />
            <Command
              editor={editor}
              label="Remover coluna"
              icon={Trash2}
              shown="coluna"
              tone="perigo"
              run={() => editor.chain().focus().deleteColumn().run()}
            />
            <Command
              editor={editor}
              label="Remover tabela"
              icon={Grid2x2X}
              tone="perigo"
              run={() => editor.chain().focus().deleteTable().run()}
            />
          </>
        ) : (
          <Command
            editor={editor}
            label="Inserir tabela"
            icon={TableIcon}
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
  icon,
}: {
  editor: Editor;
  mark: "bold" | "italic" | "underline" | "strike";
  label: string;
  icon: LucideIcon;
}) {
  return (
    <Command
      editor={editor}
      label={label}
      icon={icon}
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
  icon,
}: {
  editor: Editor;
  align: Alignment;
  label: string;
  icon: LucideIcon;
}) {
  return (
    <Command
      editor={editor}
      label={label}
      icon={icon}
      active={editor.isActive({ textAlign: align })}
      run={() => editor.chain().focus().setTextAlign(align).run()}
    />
  );
}

/**
 * Um botão da barra. O nome acessível é o `label`, em aria-label e title; o
 * ícone é só desenho. `shown` acrescenta uma palavra visível ao lado do
 * ícone, para os comandos que um ícone sozinho não distingue.
 *
 * O `onMouseDown` com preventDefault é essencial: mantém o foco dentro da
 * área editável, para que o comando se aplique à seleção atual.
 */
function Command({
  label,
  icon: Icon,
  shown,
  active = false,
  tone,
  run,
}: {
  editor: Editor;
  label: string;
  icon: LucideIcon;
  shown?: string;
  active?: boolean;
  tone?: "perigo";
  run: () => void;
}) {
  const classes = ["richtext-button"];
  if (active) classes.push("ativo");
  if (shown) classes.push("rotulado");
  if (tone) classes.push(tone);
  return (
    <button
      type="button"
      className={classes.join(" ")}
      aria-label={label}
      aria-pressed={active}
      title={label}
      onMouseDown={(e) => e.preventDefault()}
      onClick={run}
    >
      <Icon aria-hidden="true" />
      {shown && <span>{shown}</span>}
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
