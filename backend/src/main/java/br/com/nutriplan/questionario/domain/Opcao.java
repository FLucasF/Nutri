package br.com.nutriplan.questionario.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma alternativa de resposta, com a pontuacao que ela vale.
 *
 * As opcoes vivem numa coluna de texto, no formato "Nunca=0|As vezes=1". Nao e
 * tabela porque nada consulta opcao isoladamente — ela so existe dentro da
 * pergunta, e sempre inteira. Uma tabela acrescentaria uma juncao a cada
 * leitura de formulario sem responder a nenhuma pergunta nova.
 *
 * @param rotulo o texto que o paciente le
 * @param pontos quanto vale; nulo quando o questionario nao pontua
 */
public record Opcao(String rotulo, Integer pontos) {

    private static final String SEPARADOR_DE_OPCOES = "\\|";
    private static final char SEPARADOR_DE_PONTOS = '=';

    public static List<Opcao> analisar(String texto) {
        List<Opcao> opcoes = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return opcoes;
        }
        for (String parte : texto.split(SEPARADOR_DE_OPCOES)) {
            String limpo = parte.trim();
            if (limpo.isEmpty()) {
                continue;
            }
            int sinal = limpo.lastIndexOf(SEPARADOR_DE_PONTOS);
            if (sinal > 0) {
                try {
                    opcoes.add(new Opcao(limpo.substring(0, sinal).trim(),
                            Integer.parseInt(limpo.substring(sinal + 1).trim())));
                    continue;
                } catch (NumberFormatException ignorado) {
                    // O sinal de igual fazia parte do rotulo, e nao da pontuacao.
                }
            }
            opcoes.add(new Opcao(limpo, null));
        }
        return opcoes;
    }

    /** Pontos da alternativa escolhida, ou nulo se ela nao existe na lista. */
    public static Integer pontosDe(String texto, String escolha) {
        for (Opcao opcao : analisar(texto)) {
            if (opcao.rotulo().equalsIgnoreCase(escolha == null ? "" : escolha.trim())) {
                return opcao.pontos();
            }
        }
        return null;
    }
}
