package br.com.nutriplan.questionnaire.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One answer alternative, with the score it is worth.
 *
 * The options live in a text column, in the format "Nunca=0|As vezes=1". It is
 * not a table because nothing queries an option on its own — it only exists
 * inside the question, and always whole. A table would add one join to every
 * read of a form without answering any new question.
 *
 * @param label  the text the patient reads
 * @param points how much it is worth; null when the questionnaire does not score
 */
public record Option(String label, Integer points) {

    private static final String OPTIONS_SEPARATOR = "\\|";
    private static final char POINTS_SEPARATOR = '=';
    /** How the chosen alternatives of a multiple-choice answer are joined. */
    public static final String CHOICES_SEPARATOR = "; ";

    public static List<Option> analyze(String text) {
        List<Option> options = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return options;
        }
        for (String part : text.split(OPTIONS_SEPARATOR)) {
            String clean = part.trim();
            if (clean.isEmpty()) {
                continue;
            }
            int sign = clean.lastIndexOf(POINTS_SEPARATOR);
            if (sign > 0) {
                try {
                    options.add(new Option(clean.substring(0, sign).trim(),
                            Integer.parseInt(clean.substring(sign + 1).trim())));
                    continue;
                } catch (NumberFormatException ignored) {
                    // The equals sign was part of the label, and not of the score.
                }
            }
            options.add(new Option(clean, null));
        }
        return options;
    }

    /** Points of the alternative chosen, or null if it does not exist in the list. */
    public static Integer pointsDe(String text, String choice) {
        for (Option option : analyze(text)) {
            if (option.label().equalsIgnoreCase(choice == null ? "" : choice.trim())) {
                return option.points();
            }
        }
        return null;
    }

    /**
     * The sum of the points of several chosen alternatives ("A; B"), for a
     * multiple-choice answer. Null when none of them scores.
     */
    public static Integer pointsOfAll(String text, String choices) {
        if (choices == null || choices.isBlank()) {
            return null;
        }
        Integer sum = null;
        for (String choice : choices.split(";")) {
            Integer points = pointsDe(text, choice);
            if (points != null) {
                sum = (sum == null ? 0 : sum) + points;
            }
        }
        return sum;
    }
}
