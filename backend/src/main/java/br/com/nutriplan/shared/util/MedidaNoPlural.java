package br.com.nutriplan.shared.util;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Concorda a medida caseira com a quantidade prescrita.
 *
 * Existe porque a medida deixou de ser um detalhe: na pagina do paciente ela e
 * o maior texto da tela, e "2 unidade" salta aos olhos. As descricoes vem de
 * tres origens diferentes (acervo curado, IBGE e rotulo de fabricante) e nao ha
 * campo de plural em nenhuma delas, entao a concordancia e derivada.
 *
 * O escopo e deliberadamente estreito. Flexiona o substantivo que abre a
 * descricao — que e sempre o utensilio — e, quando existe, o qualificador que o
 * segue ("concha media" vira "conchas medias"). Nao toca em nada que venha
 * depois de preposicao ("colher de sopa" nao vira "colher de sopas") nem em
 * descricoes com parenteses ou barra, que no IBGE carregam anotacao da pesquisa
 * ("lata (n. e.)", "colher de arroz/servir") e nao texto corrente.
 */
public final class MedidaNoPlural {

    private MedidaNoPlural() {
    }

    /** Palavras que sinalizam que o proximo termo nao e o utensilio. */
    private static final Set<String> PREPOSICOES =
            Set.of("de", "da", "do", "das", "dos", "em", "no", "na", "com", "para", "a", "e");

    /**
     * @param quantidade quantidade prescrita; plural a partir de mais de uma
     * @param descricao  descricao da medida como veio da fonte
     */
    public static String concordar(BigDecimal quantidade, String descricao) {
        if (descricao == null || descricao.isBlank()) {
            return descricao;
        }
        if (quantidade == null || quantidade.compareTo(BigDecimal.ONE) <= 0) {
            return descricao;
        }
        // Anotacao da pesquisa, nao texto corrente: fica como esta.
        if (descricao.indexOf('(') >= 0 || descricao.indexOf('/') >= 0) {
            return descricao;
        }

        String[] palavras = descricao.trim().split("\\s+");
        if (!ehPalavra(palavras[0])) {
            return descricao;
        }
        palavras[0] = pluralizar(palavras[0]);

        // O qualificador so concorda se estiver colado ao utensilio. Em "colher
        // de sopa cheia" ele esta; em "colher de sopa", "sopa" vem de "de".
        int ultima = palavras.length - 1;
        if (ultima > 0 && ehPalavra(palavras[ultima])
                && !PREPOSICOES.contains(palavras[ultima - 1].toLowerCase())) {
            palavras[ultima] = pluralizar(palavras[ultima]);
        }
        return String.join(" ", palavras);
    }

    private static boolean ehPalavra(String texto) {
        return texto.matches("(?iu)\\p{L}+");
    }

    /** Regras suficientes para o vocabulario de utensilios de cozinha. */
    private static String pluralizar(String palavra) {
        String minuscula = palavra.toLowerCase();
        if (minuscula.endsWith("s")) {
            return palavra;                                   // ja plural ou invariavel
        }
        if (minuscula.endsWith("ao")) {
            return palavra.substring(0, palavra.length() - 2) + "oes";
        }
        if (minuscula.endsWith("ão")) {                  // -ao com til: porcao
            return palavra.substring(0, palavra.length() - 2) + "ões";
        }
        if (minuscula.endsWith("m")) {
            return palavra.substring(0, palavra.length() - 1) + "ns";
        }
        if (minuscula.endsWith("r") || minuscula.endsWith("z")) {
            return palavra + "es";                            // colher -> colheres
        }
        if (minuscula.endsWith("l")) {
            return palavra.substring(0, palavra.length() - 1) + "is";
        }
        return palavra + "s";
    }
}
