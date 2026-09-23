import { useMemo, useState } from "react";

import { RichTextEditor } from "./LazyEditor";
import { RichTextView } from "./RichTextView";
import { emptyDoc, isEmptyDoc, parseRichDoc } from "./document";

/**
 * Um campo de observação com o editor de texto do sistema.
 *
 * "Todo local que eu consiga digitar um texto, tipo observações, deve ter essa
 * opção que já existe em um que eu não achei, de formatar texto e talz, colocar
 * coluna."
 *
 * O editor já existia — na anamnese, na observação da refeição, nas orientações
 * — e é o mesmo que traz negrito, lista, tamanho de fonte e tabela. Faltava
 * estar nos outros seis lugares onde se escreve. Este componente é o que evita
 * que sejam seis cópias: a conversão entre o documento e o texto guardado é a
 * mesma em todos, e uma cópia divergiria no primeiro ajuste feito de um lado só.
 *
 * O valor entra e sai como string — o JSON do documento —, que é o que a API
 * guarda. String vazia quer dizer "sem observação", e não um documento em
 * branco: assim o campo continua podendo ser omitido no corpo do pedido.
 */
export function NotesField({
  label,
  value,
  onChange,
  minHeight = "8rem",
  help,
  /**
   * Monta o editor só quando alguém vai escrever.
   *
   * O ProseMirror é caro, e uma tela com seis observações fechadas não deve
   * pagar por seis instâncias vivas. Quem só lê usa o renderizador leve.
   */
  onDemand = false,
  /**
   * Só leitura.
   *
   * Um plano encerrado, por exemplo: o texto continua à vista, e a barra de
   * formatação some junto com a possibilidade de escrever. Deixar o editor
   * ativo ali convidava a digitar num campo que não seria gravado.
   */
  disabled = false,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  minHeight?: string;
  help?: string;
  onDemand?: boolean;
  disabled?: boolean;
}) {
  const doc = useMemo(() => parseRichDoc(value) ?? emptyDoc(), [value]);
  const empty = isEmptyDoc(doc);
  const [open, setOpen] = useState(!onDemand || !empty);

  function write(next: ReturnType<typeof emptyDoc>) {
    onChange(isEmptyDoc(next) ? "" : JSON.stringify(next));
  }

  if (disabled) {
    if (empty) return null;
    return (
      <div className="field">
        <label>{label}</label>
        <RichTextView doc={doc} empty="" />
      </div>
    );
  }

  if (!open) {
    return (
      <div className="field">
        {/* Sem `htmlFor`: o editor não é um campo nativo. Quem usa leitor de
            tela recebe o nome pelo aria-label que o próprio editor põe. */}
        <label>{label}</label>
        <button type="button" className="link-voltar minusculo" onClick={() => setOpen(true)}>
          + escrever
        </button>
      </div>
    );
  }

  return (
    <div className="field">
      <label>{label}</label>
      <RichTextEditor value={doc} label={label} minHeight={minHeight} onChange={write} />
      {help && <span className="minusculo">{help}</span>}
    </div>
  );
}

/**
 * A mesma observação, só para leitura.
 *
 * Existe aqui junto porque as duas formas andam em par: onde há um campo, em
 * alguma outra tela há a leitura do que ele guardou.
 */
export function NotesView({ value, empty = "" }: { value?: string; empty?: string }) {
  const doc = useMemo(() => parseRichDoc(value ?? "") ?? emptyDoc(), [value]);
  return <RichTextView doc={doc} empty={empty} />;
}
