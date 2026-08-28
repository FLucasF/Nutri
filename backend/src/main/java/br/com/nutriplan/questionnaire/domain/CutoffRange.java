package br.com.nutriplan.questionnaire.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One score band and what it means.
 *
 * It lives in text, in the format "0-5=Baixo|6-10=Moderado|11-99=Alto", for the
 * same reason as the options: it is always read whole, together with the
 * questionnaire.
 *
 * The band used is copied into the answer. If it stayed only on the
 * questionnaire, correcting a cutoff point would reclassify old answers — and a
 * patient's classification is the record of what was concluded on that day.
 */
public record CutoffRange(int minimum, int maximum, String classification) {

    public static List<CutoffRange> analyze(String text) {
        List<CutoffRange> ranges = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return ranges;
        }
        for (String part : text.split("\\|")) {
            String clean = part.trim();
            int sign = clean.indexOf('=');
            int dash = clean.indexOf('-');
            if (sign < 0 || dash < 0 || dash > sign) {
                continue;
            }
            try {
                ranges.add(new CutoffRange(
                        Integer.parseInt(clean.substring(0, dash).trim()),
                        Integer.parseInt(clean.substring(dash + 1, sign).trim()),
                        clean.substring(sign + 1).trim()));
            } catch (NumberFormatException ignored) {
                // A crooked band does not bring the questionnaire down: it is
                // left without a classification, which is better than
                // classifying it wrong.
            }
        }
        return ranges;
    }

    public static String classify(String text, Integer score) {
        if (score == null) {
            return null;
        }
        for (CutoffRange range : analyze(text)) {
            if (score >= range.minimum() && score <= range.maximum()) {
                return range.classification();
            }
        }
        return null;
    }
}
