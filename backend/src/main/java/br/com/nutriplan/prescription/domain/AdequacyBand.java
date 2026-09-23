package br.com.nutriplan.prescription.domain;

import java.math.BigDecimal;

/**
 * Where a prescribed amount sits against what was planned.
 *
 * The bands are the client's, stated in the document: "se ficar entre 95 a
 * 105% do teórico eu estaria dentro do planejado, quero que a cor do label
 * mude para que eu tenha esse recurso visual, se extrapolar os 105% também,
 * em outra cor".
 *
 * Three bands and not two because under and over are different clinical
 * situations. Painting both red would tell him something is wrong without
 * telling him which way to move.
 */
public enum AdequacyBand {

    /** Below 95% of the target. */
    BELOW("Abaixo do planejado"),

    /** Between 95% and 105%, inclusive. The plan is where it was meant to be. */
    WITHIN("Dentro do planejado"),

    /** Above 105% of the target. */
    ABOVE("Acima do planejado");

    private static final BigDecimal FLOOR = BigDecimal.valueOf(95);
    private static final BigDecimal CEILING = BigDecimal.valueOf(105);

    private final String description;

    AdequacyBand(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Classifies an adequacy percentage.
     *
     * @return null when there is nothing to compare against — no target, or
     *         nothing prescribed yet. A band invented from a missing number
     *         would colour the screen with a conclusion nobody reached.
     */
    public static AdequacyBand of(BigDecimal adequacyPct) {
        if (adequacyPct == null) {
            return null;
        }
        if (adequacyPct.compareTo(FLOOR) < 0) {
            return BELOW;
        }
        return adequacyPct.compareTo(CEILING) > 0 ? ABOVE : WITHIN;
    }
}
