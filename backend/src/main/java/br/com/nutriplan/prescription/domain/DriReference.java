package br.com.nutriplan.prescription.domain;

import java.util.ArrayList;
import java.util.List;

import br.com.nutriplan.patient.domain.Sex;

/**
 * As ingestões dietéticas de referência (DRI) do Institute of Medicine /
 * National Academies, por sexo e faixa de idade, dos micronutrientes que as
 * tabelas de alimentos do sistema trazem.
 *
 * "Micronutrientes × DRI": o cardápio soma cálcio, ferro, vitamina C… e o
 * número sozinho não diz se basta. Aqui cada um ganha a sua referência:
 *
 *   - RDA, quando existe (a ingestão que cobre 97–98% das pessoas saudáveis);
 *   - AI, quando não há dado para uma RDA (potássio, manganês, fibra);
 *   - e, para o sódio, o limite: a CDRR de 2019, acima da qual reduzir a
 *     ingestão diminui o risco crônico. Sódio não se "atinge", se respeita.
 *
 * Crianças de 1 a 18 anos e adultos por faixa. Gestantes e lactantes têm
 * valores próprios e não estão aqui: a tela diz isso em vez de aplicar a
 * referência errada. Menores de um ano também ficam de fora.
 *
 * Potássio e sódio são os da revisão de 2019 (NASEM); os demais, das DRI de
 * 1997–2011. A niacina das tabelas é niacina pré-formada, e a RDA é em
 * equivalentes de niacina — a comparação subestima o que o cardápio entrega.
 */
public final class DriReference {

    private DriReference() {
    }

    public enum Kind {
        RDA("RDA"), AI("AI"), LIMIT("Limite (CDRR)");

        private final String description;

        Kind(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /** A referência de um nutriente para uma pessoa. */
    public record Target(String nutrient, String label, String unit, double value, Kind kind) {}

    /*
     * Faixas: 0 = 1–3 anos, 1 = 4–8, 2 = 9–13, 3 = 14–18, 4 = 19–30,
     * 5 = 31–50, 6 = 51–70, 7 = mais de 70.
     */
    private record Row(String nutrient, String label, String unit, Kind kind,
                       double[] male, double[] female) {}

    private static double[] same(double... values) {
        return values;
    }

    private static final List<Row> ROWS = List.of(
            new Row("calciumMg", "Cálcio", "mg", Kind.RDA,
                    same(700, 1000, 1300, 1300, 1000, 1000, 1000, 1200),
                    same(700, 1000, 1300, 1300, 1000, 1000, 1200, 1200)),
            new Row("ironMg", "Ferro", "mg", Kind.RDA,
                    same(7, 10, 8, 11, 8, 8, 8, 8),
                    same(7, 10, 8, 15, 18, 18, 8, 8)),
            new Row("magnesiumMg", "Magnésio", "mg", Kind.RDA,
                    same(80, 130, 240, 410, 400, 420, 420, 420),
                    same(80, 130, 240, 360, 310, 320, 320, 320)),
            new Row("phosphorusMg", "Fósforo", "mg", Kind.RDA,
                    same(460, 500, 1250, 1250, 700, 700, 700, 700),
                    same(460, 500, 1250, 1250, 700, 700, 700, 700)),
            new Row("potassiumMg", "Potássio", "mg", Kind.AI,
                    same(2000, 2300, 2500, 3000, 3400, 3400, 3400, 3400),
                    same(2000, 2300, 2300, 2300, 2600, 2600, 2600, 2600)),
            new Row("zincMg", "Zinco", "mg", Kind.RDA,
                    same(3, 5, 8, 11, 11, 11, 11, 11),
                    same(3, 5, 8, 9, 8, 8, 8, 8)),
            new Row("copperMg", "Cobre", "mg", Kind.RDA,
                    same(0.34, 0.44, 0.7, 0.89, 0.9, 0.9, 0.9, 0.9),
                    same(0.34, 0.44, 0.7, 0.89, 0.9, 0.9, 0.9, 0.9)),
            new Row("manganeseMg", "Manganês", "mg", Kind.AI,
                    same(1.2, 1.5, 1.9, 2.2, 2.3, 2.3, 2.3, 2.3),
                    same(1.2, 1.5, 1.6, 1.6, 1.8, 1.8, 1.8, 1.8)),
            new Row("vitaminCMg", "Vitamina C", "mg", Kind.RDA,
                    same(15, 25, 45, 75, 90, 90, 90, 90),
                    same(15, 25, 45, 65, 75, 75, 75, 75)),
            new Row("thiaminMg", "Tiamina (B1)", "mg", Kind.RDA,
                    same(0.5, 0.6, 0.9, 1.2, 1.2, 1.2, 1.2, 1.2),
                    same(0.5, 0.6, 0.9, 1.0, 1.1, 1.1, 1.1, 1.1)),
            new Row("riboflavinMg", "Riboflavina (B2)", "mg", Kind.RDA,
                    same(0.5, 0.6, 0.9, 1.3, 1.3, 1.3, 1.3, 1.3),
                    same(0.5, 0.6, 0.9, 1.0, 1.1, 1.1, 1.1, 1.1)),
            new Row("niacinMg", "Niacina (B3)", "mg", Kind.RDA,
                    same(6, 8, 12, 16, 16, 16, 16, 16),
                    same(6, 8, 12, 14, 14, 14, 14, 14)),
            new Row("pyridoxineMg", "Piridoxina (B6)", "mg", Kind.RDA,
                    same(0.5, 0.6, 1.0, 1.3, 1.3, 1.3, 1.7, 1.7),
                    same(0.5, 0.6, 1.0, 1.2, 1.3, 1.3, 1.5, 1.5)),
            new Row("raeMcg", "Vitamina A (RAE)", "mcg", Kind.RDA,
                    same(300, 400, 600, 900, 900, 900, 900, 900),
                    same(300, 400, 600, 700, 700, 700, 700, 700)),
            new Row("fiberG", "Fibra alimentar", "g", Kind.AI,
                    same(19, 25, 31, 38, 38, 38, 30, 30),
                    same(19, 25, 26, 26, 25, 25, 21, 21)),
            new Row("sodiumMg", "Sódio", "mg", Kind.LIMIT,
                    same(1200, 1500, 1800, 2300, 2300, 2300, 2300, 2300),
                    same(1200, 1500, 1800, 2300, 2300, 2300, 2300, 2300)));

    /** A faixa da idade, ou -1 abaixo de um ano. */
    static int stage(int age) {
        if (age < 1) return -1;
        if (age <= 3) return 0;
        if (age <= 8) return 1;
        if (age <= 13) return 2;
        if (age <= 18) return 3;
        if (age <= 30) return 4;
        if (age <= 50) return 5;
        if (age <= 70) return 6;
        return 7;
    }

    /** "19 a 30 anos": como a faixa aparece na tela. */
    public static String stageDescription(int age) {
        return switch (stage(age)) {
            case 0 -> "1 a 3 anos";
            case 1 -> "4 a 8 anos";
            case 2 -> "9 a 13 anos";
            case 3 -> "14 a 18 anos";
            case 4 -> "19 a 30 anos";
            case 5 -> "31 a 50 anos";
            case 6 -> "51 a 70 anos";
            case 7 -> "mais de 70 anos";
            default -> "menos de 1 ano";
        };
    }

    /** As referências da pessoa; vazio abaixo de um ano. */
    public static List<Target> forPerson(Sex sex, int age) {
        int stage = stage(age);
        List<Target> out = new ArrayList<>();
        if (stage < 0 || sex == null) {
            return out;
        }
        for (Row row : ROWS) {
            double value = (sex == Sex.MALE ? row.male() : row.female())[stage];
            out.add(new Target(row.nutrient(), row.label(), row.unit(), value, row.kind()));
        }
        return out;
    }
}
