/**
 * A marca do que foi criado pelo consultório.
 *
 * "TAG sua em tudo que for feito pelo nutricionista para entender que é o
 * nutricionista criou."
 *
 * O dado sempre existiu, mas cada tela o chamava de um jeito: o alimento
 * aparecia com o nome da fonte em verde, o painel de exames dizia "meu", a
 * medida caseira não dizia nada. Três vocabulários para a mesma distinção
 * obrigam quem usa a reaprender a leitura em cada tela — e a distinção importa,
 * porque o que é do consultório é o que ele pode editar e o que só existe para
 * ele.
 *
 * Por isso a marca é um componente e não uma classe de CSS: assim a palavra é
 * a mesma em todo lugar, e mudá-la é mudar num lugar só.
 */
export function Own({ children = "sua" }: { children?: string }) {
  return (
    <span className="tag verde" title="Cadastro do seu consultório — só você vê e só você edita">
      {children}
    </span>
  );
}

/**
 * A marca do que vem das tabelas públicas.
 *
 * É o contrário da de cima, e existe pelo mesmo motivo: sem ela, "sem marca"
 * teria que ser lido como "de referência", que é uma conclusão a que só se
 * chega sabendo que a outra marca existe.
 */
export function Reference({ children }: { children: string }) {
  return (
    <span className="tag" title="Tabela de referência pública — igual para todos os consultórios">
      {children}
    </span>
  );
}
