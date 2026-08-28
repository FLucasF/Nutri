package br.com.nutriplan.shared.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV line splitting that respects quotes.
 *
 * It exists because {@code split(",")} breaks silently on real data: the TACO
 * descriptions carry commas ("Arroz, integral, cozido") and the IBGE measures
 * use a decimal comma in the very name of the utensil ("garrafa (1,5 l)"). In
 * both cases the naive splitter produces shifted columns, and the row is
 * discarded further on by a conversion error that does not point at the cause.
 */
public final class CsvReader {

    private CsvReader() {
    }

    public static String[] divide(String row) {
        return divide(row, ',');
    }

    public static String[] divide(String row, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean betweenQuotes = false;

        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                // Doubled quotes inside a quoted field represent
                // a literal quote.
                if (betweenQuotes && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    betweenQuotes = !betweenQuotes;
                }
            } else if (ch == separator && !betweenQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
