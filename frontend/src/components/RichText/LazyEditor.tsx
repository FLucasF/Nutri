import { Suspense, lazy, type ComponentProps } from "react";

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
  return (
    <Suspense
      fallback={
        <div className="richtext" style={{ minHeight: props.minHeight ?? "12rem" }}>
          <p className="empty" style={{ padding: "1rem" }}>
            Abrindo o editor…
          </p>
        </div>
      }
    >
      <Loaded {...props} />
    </Suspense>
  );
}
