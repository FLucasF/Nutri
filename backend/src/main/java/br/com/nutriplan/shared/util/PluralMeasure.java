package br.com.nutriplan.shared.util;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Agrees the household measure with the prescribed quantity.
 *
 * It exists because the measure stopped being a detail: on the patient's page
 * it is the largest text on the screen, and "2 unidade" jumps out at you. The
 * descriptions come from three different origins (curated catalog, IBGE and
 * manufacturer label) and none of them has a plural field, so agreement is
 * derived.
 *
 * The scope is deliberately narrow. It inflects the noun that opens the
 * description — which is always the utensil — and, when there is one, the
 * qualifier that follows it ("concha media" becomes "conchas medias"). It
 * touches nothing that comes after a preposition ("colher de sopa" does not
 * become "colher de sopas") and nothing in descriptions with parentheses or a
 * slash, which in IBGE carry a survey annotation ("lata (n. e.)", "colher de
 * arroz/servir") and not running text.
 */
public final class PluralMeasure {

    private PluralMeasure() {
    }

    /** Words signalling that the next term is not the utensil. */
    private static final Set<String> PREPOSITIONS =
            Set.of("de", "da", "do", "das", "dos", "em", "no", "na", "com", "ate", "a", "e");

    /**
     * @param quantity    prescribed quantity; plural from more than one
     * @param description description of the measure as it came from the source
     */
    public static String agree(BigDecimal quantity, String description) {
        if (description == null || description.isBlank()) {
            return description;
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ONE) <= 0) {
            return description;
        }
        // A survey annotation, not running text: it stays as it is.
        if (description.indexOf('(') >= 0 || description.indexOf('/') >= 0) {
            return description;
        }

        String[] words = description.trim().split("\\s+");
        if (!isWord(words[0])) {
            return description;
        }
        words[0] = pluralize(words[0]);

        // The qualifier only agrees if it is glued to the utensil. In "colher
        // de sopa cheia" it is; in "colher de sopa", "sopa" comes from "de".
        int last = words.length - 1;
        if (last > 0 && isWord(words[last])
                && !PREPOSITIONS.contains(words[last - 1].toLowerCase())) {
            words[last] = pluralize(words[last]);
        }
        return String.join(" ", words);
    }

    private static boolean isWord(String text) {
        return text.matches("(?iu)\\p{L}+");
    }

    /** Rules that are enough for the vocabulary of kitchen utensils. */
    private static String pluralize(String word) {
        String lowercase = word.toLowerCase();
        if (lowercase.endsWith("s")) {
            return word;                                   // already plural or invariable
        }
        if (lowercase.endsWith("on")) {
            return word.substring(0, word.length() - 2) + "oes";
        }
        if (lowercase.endsWith("ão")) {                  // -ao with a tilde: porcao
            return word.substring(0, word.length() - 2) + "ões";
        }
        if (lowercase.endsWith("m")) {
            return word.substring(0, word.length() - 1) + "ns";
        }
        if (lowercase.endsWith("r") || lowercase.endsWith("z")) {
            return word + "es";                            // colher -> colheres
        }
        if (lowercase.endsWith("l")) {
            return word.substring(0, word.length() - 1) + "is";
        }
        return word + "s";
    }
}
