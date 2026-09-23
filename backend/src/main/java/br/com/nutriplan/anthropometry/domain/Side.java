package br.com.nutriplan.anthropometry.domain;

/**
 * Which side of the body a measurement came from.
 *
 * {@link #SINGLE} is not a filler: it means the measurement has no side, either
 * because the site has none — a waist has one circumference — or because
 * whoever measured did not record which arm it was. The second case is real,
 * and turning it into RIGHT to tidy the data would be inventing a fact that
 * nobody observed.
 */
public enum Side {

    SINGLE("—"),
    RIGHT("Direito"),
    LEFT("Esquerdo");

    private final String description;

    Side(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
