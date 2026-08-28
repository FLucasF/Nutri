package br.com.nutriplan.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Writes a monetary amount in words, in Portuguese.
 *
 * A receipt traditionally carries the amount written out as well as in figures,
 * and the reason is practical: it makes tampering with a single digit harder.
 * It covers amounts up to the millions, a range that is enough for a practice.
 */
public final class ValueByWords {

    private static final String[] UNITS = {
            "", "um", "dois", "três", "quatro", "five", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete",
            "dezoito", "dezenove"
    };

    private static final String[] TENS = {
            "", "", "vinte", "trinta", "quarenta", "cinquenta",
            "sessenta", "setenta", "oitenta", "noventa"
    };

    private static final String[] HUNDREDS = {
            "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "seiscentos", "setecentos", "oitocentos", "novecentos"
    };

    private ValueByWords() {
    }

    public static String inBrl(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal arredondado = value.setScale(2, RoundingMode.HALF_UP);
        long brl = arredondado.longValue();
        int cents = arredondado.subtract(BigDecimal.valueOf(brl))
                .movePointRight(2).abs().intValue();

        StringBuilder text = new StringBuilder();
        if (brl > 0) {
            String words = byWords(brl);
            // In Portuguese, orders of a million require the preposition: one says
            // "um milhao DE reais", but "mil reais" with no preposition.
            String link = endsEmMillionOrder(words) ? " de" : "";
            text.append(words)
                 .append(link)
                 .append(brl == 1 ? " real" : " reais");
        }
        if (cents > 0) {
            if (text.length() > 0) {
                text.append(" e ");
            }
            text.append(byWords(cents))
                 .append(cents == 1 ? " centavo" : " centavos");
        }
        return text.length() == 0 ? "zero reais" : text.toString();
    }

    private static boolean endsEmMillionOrder(String words) {
        return words.endsWith("milhão") || words.endsWith("milhões");
    }

    private static String byWords(long number) {
        if (number == 0) {
            return "zero";
        }
        if (number >= 1_000_000) {
            long millions = number / 1_000_000;
            long remainder = number % 1_000_000;
            String text = (millions == 1 ? "um milhão" : byWords(millions) + " milhões");
            return remainder == 0 ? text : text + join(remainder) + byWords(remainder);
        }
        if (number >= 1000) {
            long thousands = number / 1000;
            long remainder = number % 1000;
            String text = (thousands == 1 ? "mil" : byWords(thousands) + " mil");
            return remainder == 0 ? text : text + join(remainder) + byWords(remainder);
        }
        if (number == 100) {
            return "cem";
        }
        if (number >= 100) {
            long hundred = number / 100;
            long remainder = number % 100;
            return HUNDREDS[(int) hundred] + (remainder == 0 ? "" : " e " + byWords(remainder));
        }
        if (number >= 20) {
            long ten = number / 10;
            long remainder = number % 10;
            return TENS[(int) ten] + (remainder == 0 ? "" : " e " + UNITS[(int) remainder]);
        }
        return UNITS[(int) number];
    }

    /**
     * Joins the larger order to the remainder. It uses "e" when the remainder
     * is smaller than a hundred or a round hundred — "mil e duzentos", but
     * "mil duzentos e cinquenta".
     */
    private static String join(long remainder) {
        return (remainder < 100 || remainder % 100 == 0) ? " e " : " ";
    }
}
