import { Suspense, lazy, type CSSProperties, type ComponentProps } from "react";

import type { RichTextEditor as Editor } from "./RichTextEditor";

/**
 * O editor, carregado só quando alguém vai escrever.
 *
 * O ProseMirror responde por cerca de dois terços do JavaScript da aplicação, e
 * quem abre a lista de pacientes nunca digita nada. Importado direto, esse peso
 * entraria no primeiro carregamento de toda tela — inclusive a de acesso.
 *
 * Quem só lê texto salvo usa `RichTextView`, que é React puro e não traz nada
 * disso junto.
 */
const Loaded = lazy(() =>
  import("./RichTextEditor").then((module) => ({ default: module.RichTextEditor })),
);

export function RichTextEditor(props: ComponentProps<typeof Editor>) {
  // A mesma moldura do editor pronto — barra vazia da mesma altura e a mesma
  // altura mínima — para a página não pular quando o editor de fato chega.
  const frame = { "--richtext-min-h": props.minHeight ?? "12rem" } as CSSProperties;
  return (
    <Suspense
      fallback={
        <div className="richtext" style={frame}>
          {!props.onlyRead && <div className="richtext-bar" aria-hidden="true" />}
          <p className="empty richtext-loading">Abrindo o editor…</p>
        </div>
      }
    >
      <Loaded {...props} />
    </Suspense>
  );
}
