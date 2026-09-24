package br.com.nutriplan.anthropometry.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import br.com.nutriplan.anthropometry.domain.Side;
import br.com.nutriplan.anthropometry.domain.Skinfold;
import br.com.nutriplan.anthropometry.dto.AnthropometryDtos;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.richtext.RichTextPdf;
import lombok.RequiredArgsConstructor;

/**
 * O relatório de uma avaliação, em duas versões.
 *
 * "Adicionar funcionalidade de gerar relatórios": o relatório de evolução já
 * existia, mas exigia duas avaliações — e o cliente o testou com uma. Este
 * sai de qualquer avaliação.
 *
 * A versão do profissional traz tudo o que a tela mostra, com as dobras, as
 * circunferências, o protocolo e a densidade. A do paciente fala com ele: o
 * peso, o IMC e o que ele quer dizer, a faixa de peso saudável, a gordura com
 * a palavra da classificação, o que mudou desde a última vez e o gráfico do
 * peso. Nada de protocolo, densidade ou soma de dobras: é o papel que o
 * paciente leva para casa, e ele não precisa aprender antropometria para lê-lo.
 */
@Component
@RequiredArgsConstructor
public class AssessmentReportGenerator {

    public enum Audience { PROFESSIONAL, PATIENT }

    /** O que o relatório precisa: a avaliação, as de referência e quem assina. */
    public record Input(
            AnthropometryDtos.AssessmentResponse current,
            /** A avaliação anterior, para a comparação. Nula na primeira. */
            AnthropometryDtos.AssessmentResponse previous,
            /** A primeira, quando é outra além da anterior. */
            AnthropometryDtos.AssessmentResponse first,
            /** O peso de cada avaliação até esta, para o gráfico. */
            List<PdfLineChart.Point> weightSeries,
            String practiceName,
            String professionalName,
            String crn
    ) {}

    private static final Color PITCH = new Color(0x13, 0x1c, 0x18);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);
    private static final Color HEADER = new Color(0xf1, 0xf3, 0xef);
    private static final Color ACCENT = new Color(0x8c, 0x2f, 0x51);

    private static final Font TITLE = font(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = font(10, Font.NORMAL, MEAN_INK);
    private static final Font SECTION = font(11.5f, Font.BOLD, PITCH);
    private static final Font LABEL = font(7.5f, Font.BOLD, MEAN_INK);
    private static final Font VALUE = font(10, Font.NORMAL, PITCH);
    private static final Font FIGURE = font(17, Font.BOLD, PITCH);
    private static final Font TEXT = font(10.5f, Font.NORMAL, PITCH);
    private static final Font SMALL = font(8.5f, Font.NORMAL, MEAN_INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final ObjectMapper mapper;

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    public byte[] generate(Input input, Audience audience) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            if (audience == Audience.PATIENT) {
                patient(document, writer, input);
            } else {
                professional(document, writer, input);
            }
            signature(document, input);
            document.close();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível gerar o relatório da avaliação.");
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------ profissional

    private void professional(Document document, PdfWriter writer, Input input) throws Exception {
        var a = input.current();
        header(document, input, "Avaliação antropométrica · " + a.date().format(DATE)
                + (a.ageYears() == null ? "" : " · " + a.ageYears() + " anos"));

        document.add(section("Medidas e índices"));
        var measures = new LinkedHashMap<String, String>();
        measures.put("Peso", kg(a.weightKg()));
        measures.put("Altura", unit(a.heightCm(), "cm"));
        measures.put("IMC", a.bmi() == null ? "—"
                : number(a.bmi()) + " kg/m²" + (a.classificationBmi().value() == null ? ""
                        : " · " + a.classificationBmi().value().getDescription()));
        measures.put("Faixa de peso saudável", a.healthyWeight() == null ? "—"
                : number(a.healthyWeight().minimumKg()) + " a " + number(a.healthyWeight().maximumKg()) + " kg");
        measures.put("Cintura/quadril", a.ratioWaistHip() == null ? "—"
                : number2(a.ratioWaistHip()) + (a.riskCardiometabolico().value() == null ? ""
                        : " · " + a.riskCardiometabolico().value().getDescription()));
        measures.put("Circ. muscular do braço", a.armMuscle().value() == null ? "—"
                : unit(a.armMuscle().value().circumferenceCm(), "cm"));
        document.add(grid(measures));

        var composition = a.composition();
        if (composition != null) {
            document.add(section("Composição corporal · " + composition.protocolDescription()));
            var body = new LinkedHashMap<String, String>();
            body.put("Gordura corporal", percent(composition.percentageFat())
                    + (composition.fatClassificationDescription() == null ? ""
                            : " · " + composition.fatClassificationDescription()));
            body.put("Faixa ideal", composition.fatIdealMin() == null ? "—"
                    : number(composition.fatIdealMin()) + " a " + number(composition.fatIdealMax()) + "%");
            body.put("Massa gorda", kg(composition.massFatKg()));
            body.put("Massa magra", kg(composition.massLeanKg()));
            // O Faulkner não passa pela densidade: a linha some em vez de dizer "—".
            if (composition.density() != null) {
                body.put("Densidade corporal", composition.density()
                        .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString()
                        .replace('.', ',') + " g/cm³");
            }
            body.put("Soma das dobras", unit(composition.skinfoldSumMm(), "mm"));
            document.add(grid(body));
        }

        var fractionation = a.fractionation();
        if (fractionation != null && (fractionation.boneMassKg().value() != null
                || fractionation.residualMassKg().value() != null
                || fractionation.muscleMassKg().value() != null)) {
            document.add(section("Fracionamento"));
            var parts = new LinkedHashMap<String, String>();
            parts.put("Massa óssea", derivedKg(fractionation.boneMassKg()));
            parts.put("Massa residual", derivedKg(fractionation.residualMassKg()));
            parts.put("Massa muscular", derivedKg(fractionation.muscleMassKg()));
            document.add(grid(parts));
        }

        var energy = a.expenditureEnergy();
        if (energy != null) {
            document.add(section("Gasto energético · " + energy.equationDescription()));
            var kcal = new LinkedHashMap<String, String>();
            kcal.put("Basal", unit(energy.basalKcal(), "kcal"));
            kcal.put("Fator de atividade", energy.factorActivity() == null ? "—" : number2(energy.factorActivity()));
            kcal.put("Total", unit(energy.totalKcal(), "kcal"));
            document.add(grid(kcal));
        }

        var bia = new LinkedHashMap<String, String>();
        putIf(bia, "% de gordura", a.biaFatPercentage(), "%");
        putIf(bia, "Massa gorda", a.biaFatMassKg(), "kg");
        putIf(bia, "% de massa muscular", a.biaMusclePercentage(), "%");
        putIf(bia, "Massa muscular", a.biaMuscleMassKg(), "kg");
        putIf(bia, "Massa livre de gordura", a.biaLeanMassKg(), "kg");
        putIf(bia, "Peso ósseo", a.biaBoneMassKg(), "kg");
        putIf(bia, "Gordura visceral", a.biaVisceralFat(), "");
        putIf(bia, "% de água corporal", a.biaBodyWaterPercentage(), "%");
        if (a.biaMetabolicAge() != null) {
            bia.put("Idade metabólica", a.biaMetabolicAge() + " anos");
        }
        if (!bia.isEmpty()) {
            document.add(section("Bioimpedância"));
            document.add(grid(bia));
        }

        var previous = input.previous();
        var skinfolds = skinfoldsOf(a);
        if (!skinfolds.isEmpty()) {
            document.add(section("Dobras cutâneas (mm)"));
            document.add(measuresTable(skinfolds, previous == null ? null : skinfoldsOf(previous),
                    previous));
        }
        var circumferences = circumferencesOf(a);
        if (!circumferences.isEmpty()) {
            document.add(section("Circunferências (cm)"));
            document.add(measuresTable(circumferences,
                    previous == null ? null : circumferencesOf(previous), previous));
        }
        var others = new LinkedHashMap<String, BigDecimal>();
        putIf(others, "Diâmetro do úmero", a.diameterHumerus());
        putIf(others, "Diâmetro do punho", a.diameterWrist());
        putIf(others, "Diâmetro do fêmur", a.diameterFemur());
        putIf(others, "Altura sentado", a.heightSittingCm());
        putIf(others, "Altura do joelho", a.heightKneeCm());
        if (!others.isEmpty()) {
            document.add(section("Diâmetros e alturas (cm)"));
            document.add(measuresTable(others, null, null));
        }

        if (previous != null) {
            document.add(section("Comparação com " + previous.date().format(DATE)));
            document.add(comparison(a, previous));
        }

        Image chart = PdfLineChart.draw(writer, "Peso (kg)", input.weightSeries(),
                document.right() - document.left(), band(a));
        if (chart != null) {
            document.add(section("Evolução do peso"));
            document.add(chart);
        }

        if (StringUtils.hasText(a.notes())) {
            document.add(section("Observações"));
            for (Element element : RichTextPdf.render(a.notes(), mapper)) {
                document.add(element);
            }
        }
    }

    // ---------------------------------------------------------------- paciente

    private void patient(Document document, PdfWriter writer, Input input) throws Exception {
        var a = input.current();
        var previous = input.previous();
        header(document, input, "Resultado da sua avaliação de " + a.date().format(DATE));

        // Os números que importam, em cartões, com o quanto mudaram desde a última vez.
        var figures = new PdfPTable(3);
        figures.setWidthPercentage(100);
        figures.setSpacingAfter(14f);
        int cells = 0;
        cells += figure(figures, "Peso", a.weightKg(), "kg",
                previous == null ? null : previous.weightKg(), previous);
        cells += figure(figures, "IMC", a.bmi(), "kg/m²",
                previous == null ? null : previous.bmi(), previous);
        if (a.composition() != null) {
            cells += figure(figures, "Gordura corporal", a.composition().percentageFat(), "%",
                    previous == null || previous.composition() == null ? null
                            : previous.composition().percentageFat(), previous);
            cells += figure(figures, "Massa magra", a.composition().massLeanKg(), "kg",
                    previous == null || previous.composition() == null ? null
                            : previous.composition().massLeanKg(), previous);
        }
        BigDecimal waist = circumference(a, "WAIST");
        if (waist != null) {
            cells += figure(figures, "Cintura", waist, "cm",
                    previous == null ? null : circumference(previous, "WAIST"), previous);
        }
        while (cells % 3 != 0) {
            var filler = new PdfPCell(new Phrase(""));
            filler.setBorder(Rectangle.NO_BORDER);
            figures.addCell(filler);
            cells++;
        }
        if (cells > 0) {
            document.add(figures);
        }

        var reading = new ArrayList<String>();
        if (a.bmi() != null && a.classificationBmi().value() != null) {
            reading.add("Seu IMC é " + number(a.bmi()) + ", na faixa de "
                    + a.classificationBmi().value().getDescription().toLowerCase() + ".");
        }
        if (a.healthyWeight() != null) {
            reading.add("Para a sua altura, a faixa de peso saudável vai de "
                    + number(a.healthyWeight().minimumKg()) + " a "
                    + number(a.healthyWeight().maximumKg()) + " kg.");
        }
        var composition = a.composition();
        if (composition != null && composition.percentageFat() != null) {
            String sentence = "Sua gordura corporal é de " + number(composition.percentageFat()) + "%";
            if (composition.fatClassificationDescription() != null) {
                sentence += ", classificada como \"" + composition.fatClassificationDescription().toLowerCase()
                        + "\" para o seu sexo e a sua idade";
            }
            sentence += ".";
            if (composition.fatIdealMin() != null) {
                sentence += " A faixa ideal vai de " + number(composition.fatIdealMin()) + " a "
                        + number(composition.fatIdealMax()) + "%.";
            }
            reading.add(sentence);
        }
        if (a.riskCardiometabolico().value() != null) {
            reading.add("A relação entre a cintura e o quadril indica risco "
                    + a.riskCardiometabolico().value().getDescription().toLowerCase()
                    + " para doenças do coração e do metabolismo.");
        }
        if (!reading.isEmpty()) {
            document.add(section("O que os números dizem"));
            for (String sentence : reading) {
                var paragraph = new Paragraph(sentence, TEXT);
                paragraph.setSpacingAfter(5f);
                paragraph.setLeading(15f);
                document.add(paragraph);
            }
        }

        var first = input.first();
        if (first != null && first.weightKg() != null && a.weightKg() != null) {
            document.add(section("Desde a primeira avaliação (" + first.date().format(DATE) + ")"));
            var since = new ArrayList<String>();
            since.add("Peso: " + change(a.weightKg().subtract(first.weightKg()), "kg"));
            BigDecimal firstWaist = circumference(first, "WAIST");
            if (waist != null && firstWaist != null) {
                since.add("Cintura: " + change(waist.subtract(firstWaist), "cm"));
            }
            if (composition != null && first.composition() != null
                    && composition.percentageFat() != null && first.composition().percentageFat() != null
                    && composition.protocol() == first.composition().protocol()) {
                since.add("Gordura corporal: " + change(
                        composition.percentageFat().subtract(first.composition().percentageFat()), "pontos"));
            }
            for (String line : since) {
                var paragraph = new Paragraph(line, TEXT);
                paragraph.setSpacingAfter(3f);
                document.add(paragraph);
            }
        }

        Image chart = PdfLineChart.draw(writer, "Seu peso ao longo do tempo (kg)", input.weightSeries(),
                document.right() - document.left(), band(a));
        if (chart != null) {
            document.add(section("Evolução"));
            document.add(chart);
        }
    }

    /** Um cartão com o número grande e o quanto mudou desde a avaliação anterior. */
    private int figure(PdfPTable table, String label, BigDecimal value, String unit,
                       BigDecimal before, AnthropometryDtos.AssessmentResponse previous) {
        if (value == null) {
            return 0;
        }
        var cell = new PdfPCell();
        cell.setBackgroundColor(HEADER);
        cell.setBorderColor(ROW);
        cell.setPadding(9f);
        var head = new Paragraph(label.toUpperCase(), LABEL);
        head.setSpacingAfter(3f);
        cell.addElement(head);
        var number = new Paragraph();
        number.add(new Chunk(number(value), FIGURE));
        number.add(new Chunk(" " + unit, SMALL));
        cell.addElement(number);
        if (before != null && previous != null) {
            var delta = new Paragraph(change(value.subtract(before), unit.equals("%") ? "pontos" : unit)
                    + " desde " + previous.date().format(DateTimeFormatter.ofPattern("dd/MM")), SMALL);
            delta.setSpacingBefore(2f);
            cell.addElement(delta);
        }
        table.addCell(cell);
        return 1;
    }

    // ------------------------------------------------------------------ comuns

    private void header(Document document, Input input, String subtitleText) throws Exception {
        if (StringUtils.hasText(input.practiceName())) {
            var practice = new Paragraph(input.practiceName(), SMALL);
            practice.setSpacingAfter(8f);
            document.add(practice);
        }
        var title = new Paragraph(input.current().patientName() == null
                ? "Avaliação antropométrica" : input.current().patientName(), TITLE);
        title.setSpacingAfter(2f);
        document.add(title);
        var subtitle = new Paragraph(subtitleText, SUBTITLE);
        subtitle.setSpacingAfter(12f);
        document.add(subtitle);
    }

    private void signature(Document document, Input input) throws Exception {
        var signature = new Paragraph();
        signature.setSpacingBefore(36f);
        signature.setAlignment(Element.ALIGN_CENTER);
        signature.add(new Chunk("______________________________________\n", SUBTITLE));
        var line = new StringBuilder();
        if (StringUtils.hasText(input.professionalName())) {
            line.append(input.professionalName());
        }
        if (StringUtils.hasText(input.crn())) {
            line.append(line.length() > 0 ? " · " : "")
                    .append(br.com.nutriplan.shared.util.CrnText.of(input.crn()));
        }
        if (line.length() == 0) {
            line.append("Nutricionista responsável");
        }
        signature.add(new Chunk(line.toString(), SUBTITLE));
        document.add(signature);
    }

    private Paragraph section(String text) {
        var paragraph = new Paragraph(text, SECTION);
        paragraph.setSpacingBefore(12f);
        paragraph.setSpacingAfter(5f);
        return paragraph;
    }

    /** Rótulo sobre valor, dois pares por linha. */
    private PdfPTable grid(Map<String, String> values) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(4f);
        for (var entry : values.entrySet()) {
            var cell = new PdfPCell();
            cell.setBorderColor(ROW);
            cell.setPadding(6f);
            var label = new Paragraph(entry.getKey().toUpperCase(), LABEL);
            label.setSpacingAfter(1f);
            cell.addElement(label);
            cell.addElement(new Phrase(entry.getValue(), VALUE));
            table.addCell(cell);
        }
        if (values.size() % 2 == 1) {
            var filler = new PdfPCell(new Phrase(""));
            filler.setBorder(Rectangle.NO_BORDER);
            table.addCell(filler);
        }
        return table;
    }

    /** Local, medida de hoje e, quando há, a da avaliação anterior. */
    private PdfPTable measuresTable(Map<String, BigDecimal> now, Map<String, BigDecimal> before,
                                    AnthropometryDtos.AssessmentResponse previous) {
        boolean compare = before != null && previous != null;
        var table = new PdfPTable(compare ? new float[] {3f, 1.4f, 1.4f} : new float[] {3f, 1.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(4f);
        head(table, "Local", Element.ALIGN_LEFT);
        head(table, "Medida", Element.ALIGN_RIGHT);
        if (compare) {
            head(table, previous.date().format(DateTimeFormatter.ofPattern("dd/MM/uu")), Element.ALIGN_RIGHT);
        }
        for (var entry : now.entrySet()) {
            cell(table, entry.getKey(), Element.ALIGN_LEFT);
            cell(table, number(entry.getValue()), Element.ALIGN_RIGHT);
            if (compare) {
                BigDecimal old = before.get(entry.getKey());
                cell(table, old == null ? "—" : number(old), Element.ALIGN_RIGHT);
            }
        }
        return table;
    }

    private PdfPTable comparison(AnthropometryDtos.AssessmentResponse now,
                                 AnthropometryDtos.AssessmentResponse before) {
        var table = new PdfPTable(new float[] {3f, 1.3f, 1.3f, 1.3f});
        table.setWidthPercentage(100);
        head(table, "Medida", Element.ALIGN_LEFT);
        head(table, before.date().format(DateTimeFormatter.ofPattern("dd/MM/uu")), Element.ALIGN_RIGHT);
        head(table, now.date().format(DateTimeFormatter.ofPattern("dd/MM/uu")), Element.ALIGN_RIGHT);
        head(table, "Diferença", Element.ALIGN_RIGHT);
        row(table, "Peso (kg)", before.weightKg(), now.weightKg());
        row(table, "IMC", before.bmi(), now.bmi());
        row(table, "Cintura (cm)", circumference(before, "WAIST"), circumference(now, "WAIST"));
        row(table, "Quadril (cm)", circumference(before, "HIP"), circumference(now, "HIP"));
        // Gordura de protocolos diferentes não se compara: a diferença seria
        // a troca de fórmula, e não o corpo.
        if (now.composition() != null && before.composition() != null
                && now.composition().protocol() == before.composition().protocol()) {
            row(table, "Gordura corporal (%)", before.composition().percentageFat(), now.composition().percentageFat());
            row(table, "Massa magra (kg)", before.composition().massLeanKg(), now.composition().massLeanKg());
        }
        return table;
    }

    private void row(PdfPTable table, String label, BigDecimal before, BigDecimal now) {
        if (before == null || now == null) {
            return;
        }
        BigDecimal difference = now.subtract(before);
        cell(table, label, Element.ALIGN_LEFT);
        cell(table, number(before), Element.ALIGN_RIGHT);
        cell(table, number(now), Element.ALIGN_RIGHT);
        var cell = new PdfPCell(new Phrase(signed(difference),
                font(9, Font.NORMAL, difference.signum() == 0 ? MEAN_INK : ACCENT)));
        cell.setBorderColor(ROW);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void head(PdfPTable table, String text, int alignment) {
        var cell = new PdfPCell(new Phrase(text.toUpperCase(), LABEL));
        cell.setBackgroundColor(HEADER);
        cell.setBorderColor(ROW);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void cell(PdfPTable table, String text, int alignment) {
        var cell = new PdfPCell(new Phrase(text, font(9, Font.NORMAL, PITCH)));
        cell.setBorderColor(ROW);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private static PdfLineChart.Band band(AnthropometryDtos.AssessmentResponse a) {
        return a.healthyWeight() == null ? null
                : new PdfLineChart.Band(a.healthyWeight().minimumKg().doubleValue(),
                        a.healthyWeight().maximumKg().doubleValue(), "faixa de peso saudável");
    }

    private static Map<String, BigDecimal> skinfoldsOf(AnthropometryDtos.AssessmentResponse a) {
        var out = new LinkedHashMap<String, BigDecimal>();
        if (a.skinfolds() == null) {
            return out;
        }
        a.skinfolds().forEach((key, value) -> {
            String label;
            try {
                label = Skinfold.valueOf(key).getDescription();
            } catch (IllegalArgumentException e) {
                label = key;
            }
            if (value != null) {
                out.put(label, value);
            }
        });
        return out;
    }

    private static Map<String, BigDecimal> circumferencesOf(AnthropometryDtos.AssessmentResponse a) {
        var out = new LinkedHashMap<String, BigDecimal>();
        if (a.circumferences() == null) {
            return out;
        }
        for (var c : a.circumferences()) {
            if (c.valueCm() == null) {
                continue;
            }
            String label = c.site().getDescription()
                    + (c.side() == Side.SINGLE ? "" : " (" + c.side().getDescription().toLowerCase() + ")");
            out.put(label, c.valueCm());
        }
        return out;
    }

    private static BigDecimal circumference(AnthropometryDtos.AssessmentResponse a, String site) {
        if (a.circumferences() == null) {
            return null;
        }
        return a.circumferences().stream()
                .filter(c -> c.site().name().equals(site) && c.side() == Side.SINGLE)
                .map(AnthropometryDtos.CircumferenceValue::valueCm)
                .findFirst().orElse(null);
    }

    private static void putIf(Map<String, String> map, String label, BigDecimal value, String unit) {
        if (value != null) {
            map.put(label, number(value) + (unit.isEmpty() ? "" : unit.equals("%") ? "%" : " " + unit));
        }
    }

    private static void putIf(Map<String, BigDecimal> map, String label, BigDecimal value) {
        if (value != null) {
            map.put(label, value);
        }
    }

    private static String derivedKg(AnthropometryDtos.Derived<BigDecimal> derived) {
        return derived.value() != null ? kg(derived.value())
                : derived.unavailableBecause() == null ? "—" : derived.unavailableBecause();
    }

    private static String kg(BigDecimal value) {
        return unit(value, "kg");
    }

    private static String percent(BigDecimal value) {
        return value == null ? "—" : number(value) + "%";
    }

    private static String unit(BigDecimal value, String unit) {
        return value == null ? "—" : number(value) + " " + unit;
    }

    private static String number(BigDecimal value) {
        return PdfLineChart.number(value.doubleValue());
    }

    private static String number2(BigDecimal value) {
        return String.format(java.util.Locale.of("pt", "BR"), "%.2f", value.doubleValue());
    }

    private static String signed(BigDecimal value) {
        String text = number(value);
        return value.signum() > 0 ? "+" + text : text;
    }

    /** "1,2 kg a menos", "3 cm a mais", "sem mudança": a diferença dita em palavras. */
    private static String change(BigDecimal difference, String unit) {
        if (difference.abs().compareTo(new BigDecimal("0.05")) < 0) {
            return "sem mudança";
        }
        String amount = number(difference.abs()) + " " + unit;
        return difference.signum() < 0 ? amount + " a menos" : amount + " a mais";
    }
}
