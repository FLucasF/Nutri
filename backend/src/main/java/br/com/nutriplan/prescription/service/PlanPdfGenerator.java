package br.com.nutriplan.prescription.service;

import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The meal plan on paper.
 *
 * It exists because the patient has no account in the system: they read the
 * plan through the link or take it printed, and the printout is the only one
 * that works in the kitchen without a battery. The sheet is handed over at the
 * appointment, so it has to stand on its own — it carries the practice, the
 * professional, the CRN and the validity period, and depends on nothing that
 * only exists on screen.
 *
 * The layout repeats the day ruler of the interface: the hour on the left, the
 * meal hanging from it, and the household measure in a larger size than the
 * food's name. It is not a flourish — it is the same reason as on screen. The
 * patient already knows what rice is; what they do not know is how much.
 */
@Component
public class PlanPdfGenerator {

    private static final Color PITCH = new Color(0x13, 0x1c, 0x18);
    private static final Color BEETROOT = new Color(0x5e, 0x1a, 0x46);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);

    private static final Font TITLE = source(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = source(10, Font.NORMAL, MEAN_INK);
    private static final Font TAG = source(7.5f, Font.BOLD, MEAN_INK);
    private static final Font HOUR = source(12, Font.BOLD, PITCH);
    private static final Font MEAL = source(13, Font.BOLD, PITCH);
    private static final Font FOOD = source(9.5f, Font.NORMAL, PITCH);
    private static final Font SERVING = source(12, Font.BOLD, BEETROOT);
    private static final Font WEIGHT = source(8, Font.NORMAL, MEAN_INK);
    private static final Font NOTE = source(8.5f, Font.ITALIC, MEAN_INK);
    private static final Font FOOTER = source(8, Font.NORMAL, MEAN_INK);

    private static Font source(float size, int style, Color color) {
        // Helvetica is one of the 14 base PDF fonts: it does not need to be
        // embedded, and no reader installation lacks it.
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    /** A4 (595pt) minus the 48pt margins declared when the document is opened. */
    private static final float WIDTH_UTIL = 595 - 96;
    /** Half a page: the figure illustrates the handout, it does not take the sheet on its own. */
    private static final float HEIGHT_FIGURE_MAXIMUM = 320;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public byte[] generate(PrescriptionDtos.PublicPlanResponse plan, boolean draft) {
        return generate(plan, draft, Map.of());
    }

    /**
     * @param figures content of the handout images, by attachment key. It comes
     *                from outside because loading a file is the work of whoever
     *                holds the transaction, not of whoever draws the sheet.
     */
    public byte[] generate(PrescriptionDtos.PublicPlanResponse plan, boolean draft,
                        Map<Long, byte[]> figures) {
        var output = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 44, 52);
        PdfWriter writer = PdfWriter.getInstance(document, output);
        writer.setPageEvent(new Footer(plan));

        document.open();
        try {
            if (draft) {
                // The professional may want to print it to check before
                // publishing. The sheet has to say what it is, or it reaches
                // the patient as if it were a prescription.
                document.add(warning("RASCUNHO — CONFERÊNCIA INTERNA, NÃO ENTREGUE AO PACIENTE"));
            }
            if (plan.closed()) {
                document.add(warning("PLANO ENCERRADO — NÃO É MAIS O PLANO VIGENTE"));
            }

            document.add(header(plan));
            if (hasText(plan.handouts())) {
                document.add(handouts(plan.handouts()));
            }
            document.add(meals(plan));
            // After the meals: whoever reads the sheet in the kitchen looks for
            // the meal, not the educational text.
            for (var attachment : plan.handoutsAttached()) {
                document.add(textBlock(attachment.title(), attachment.body()));
                Element figure = figure(figures.get(attachment.id()));
                if (figure != null) {
                    document.add(figure);
                }
            }
            if (plan.summary() != null && plan.summary().energyKcal() != null) {
                document.add(summary(plan.summary()));
            }
        } finally {
            document.close();
        }
        return output.toByteArray();
    }

    // ------------------------------------------------------------------- partes

    private Element warning(String text) {
        var cell = new PdfPCell(new Phrase(text, source(9, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(BEETROOT);
        cell.setPadding(7);
        cell.setBorder(0);
        var table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        table.addCell(cell);
        return table;
    }

    private Element header(PrescriptionDtos.PublicPlanResponse plan) {
        var block = new Paragraph();
        if (hasText(plan.practiceName())) {
            block.add(new Phrase(plan.practiceName().toUpperCase() + "\n", TAG));
        }
        block.add(new Phrase(plan.title() + "\n", TITLE));

        var row = new StringBuilder();
        if (hasText(plan.patientName())) {
            row.append(plan.patientName());
        }
        if (hasText(plan.nutritionistName())) {
            if (!row.isEmpty()) {
                row.append("  ·  ");
            }
            row.append("Elaborado por ").append(plan.nutritionistName());
            if (hasText(plan.nutritionistCrn())) {
                row.append(" (").append(plan.nutritionistCrn()).append(")");
            }
        }
        if (!row.isEmpty()) {
            block.add(new Phrase("\n" + row + "\n", SUBTITLE));
        }
        if (plan.validityStart() != null) {
            String validity = "Vigência: " + plan.validityStart().format(DATE)
                    + (plan.validityEnd() != null
                            ? " até " + plan.validityEnd().format(DATE) : " em diante");
            block.add(new Phrase(validity + "\n", TAG));
        }
        block.setSpacingAfter(16);
        return block;
    }

    /**
     * The handout figure, fitted to the usable width of the sheet.
     *
     * An image larger than the page makes the reader crop instead of shrink,
     * and the patient receives half a plate. A file the image reader does not
     * recognize disappears from the sheet in silence: the handout text is
     * already printed above, and a sheet without the figure serves better than
     * a sheet that does not come out.
     */
    private Element figure(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        try {
            var image = com.lowagie.text.Image.getInstance(content);
            image.scaleToFit(WIDTH_UTIL, HEIGHT_FIGURE_MAXIMUM);
            image.setAlignment(Element.ALIGN_LEFT);
            image.setSpacingAfter(16);
            return image;
        } catch (Exception e) {
            return null;
        }
    }

    private Element handouts(String text) {
        return textBlock("ORIENTAÇÕES GERAIS", text);
    }

    /** Text with a title, marked by the beetroot bar in the margin. */
    private Element textBlock(String title, String body) {
        var table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(16);
        table.setKeepTogether(true);

        var cell = new PdfPCell();
        cell.setBorder(PdfPCell.LEFT);
        cell.setBorderColor(BEETROOT);
        cell.setBorderWidthLeft(2.5f);
        cell.setPaddingLeft(10);
        cell.setPaddingTop(2);
        cell.setPaddingBottom(6);
        cell.addElement(new Paragraph(title.toUpperCase(), TAG));
        cell.addElement(new Paragraph(body, source(9.5f, Font.NORMAL, PITCH)));
        table.addCell(cell);
        return table;
    }

    /**
     * The meals, on the day ruler.
     *
     * Two columns: the hour and the content. The left border of the second
     * column is the vertical line that ties the day together — the same
     * element as on screen, here made of a cell border because that is what
     * the PDF knows how to draw without absolute positioning.
     */
    private Element meals(PrescriptionDtos.PublicPlanResponse plan) {
        var table = new PdfPTable(new float[] {1f, 6.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        // A meal header must not end the page on its own.
        table.setSplitLate(true);

        for (var meal : plan.meals()) {
            table.addCell(hourCell(meal));
            table.addCell(mealCell(meal));
        }
        return table;
    }

    private PdfPCell hourCell(PrescriptionDtos.PublicMealResponse meal) {
        String text = meal.time() != null
                ? meal.time().toString().substring(0, 5)
                : "LIVRE";
        var cell = new PdfPCell(new Phrase(text,
                meal.time() != null ? HOUR : TAG));
        cell.setBorder(0);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setPaddingTop(10);
        cell.setPaddingRight(10);
        return cell;
    }

    private PdfPCell mealCell(PrescriptionDtos.PublicMealResponse meal) {
        var cell = new PdfPCell();
        cell.setBorder(PdfPCell.LEFT);
        cell.setBorderColor(ROW);
        cell.setBorderWidthLeft(1f);
        cell.setPaddingLeft(12);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(14);

        cell.addElement(new Paragraph(meal.name(), MEAL));
        if (hasText(meal.notes())) {
            cell.addElement(new Paragraph(meal.notes(), NOTE));
        }

        for (var item : meal.items()) {
            var block = new Paragraph();
            block.setSpacingBefore(7);
            block.add(new Phrase(item.description() + "\n", FOOD));
            block.add(new Phrase(item.serving(), SERVING));
            if (item.weightGrams() != null && !isWeightAtGrams(item.serving())) {
                block.add(new Phrase("   " + formatWeight(item.weightGrams()), WEIGHT));
            }
            cell.addElement(block);

            if (hasText(item.notes())) {
                cell.addElement(new Paragraph(item.notes(), NOTE));
            }
            for (var substitution : item.substitutions()) {
                var choice = new Paragraph(
                        "ou  " + substitution.description() + " — " + substitution.serving(),
                        source(8.5f, Font.NORMAL, MEAN_INK));
                choice.setIndentationLeft(12);
                cell.addElement(choice);
            }
        }
        return cell;
    }

    private Element summary(PrescriptionDtos.PublicSummaryResponse summary) {
        var table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.addCell(summaryCell("ENERGIA", summary.energyKcal(), "kcal"));
        table.addCell(summaryCell("PROTEÍNAS", summary.proteinG(), "g"));
        table.addCell(summaryCell("CARBOIDRATOS", summary.carbohydrateG(), "g"));
        table.addCell(summaryCell("GORDURAS", summary.fatG(), "g"));

        var wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        var cell = new PdfPCell();
        cell.setBorder(PdfPCell.TOP);
        cell.setBorderColor(ROW);
        cell.setPaddingTop(10);
        cell.addElement(new Paragraph("RESUMO DO DIA", TAG));
        cell.addElement(table);
        cell.addElement(new Paragraph(
                "Valores estimados a partir das tabelas de composição de alimentos.",
                source(7.5f, Font.NORMAL, MEAN_INK)));
        wrapper.addCell(cell);
        return wrapper;
    }

    private PdfPCell summaryCell(String label, BigDecimal value, String unit) {
        var cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(8);
        cell.addElement(new Paragraph(label, TAG));
        cell.addElement(new Paragraph(
                value == null ? "—" : round(value) + " " + unit,
                source(13, Font.BOLD, PITCH)));
        return cell;
    }

    /** A footer on every page: the paper circulates loose, and has to identify itself. */
    private static class Footer extends com.lowagie.text.pdf.PdfPageEventHelper {
        private final PrescriptionDtos.PublicPlanResponse plan;

        Footer(PrescriptionDtos.PublicPlanResponse plan) {
            this.plan = plan;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            String left = plan.title()
                    + (plan.patientName() != null ? "  ·  " + plan.patientName() : "");
            String right = "Emitido em " + LocalDate.now().format(DATE)
                    + "  ·  página " + document.getPageNumber();

            var table = new PdfPTable(2);
            table.setTotalWidth(document.right() - document.left());
            table.addCell(cell(left, Element.ALIGN_LEFT));
            table.addCell(cell(right, Element.ALIGN_RIGHT));
            table.writeSelectedRows(0, -1, document.left(),
                    document.bottom() - 8, writer.getDirectContent());
        }

        private PdfPCell cell(String text, int alignment) {
            var cell = new PdfPCell(new Phrase(text, FOOTER));
            cell.setBorder(0);
            cell.setHorizontalAlignment(alignment);
            return cell;
        }
    }

    // ------------------------------------------------------------------- apoio

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /** The portion already is the weight itself when there is no household measure: "100 g". */
    private boolean isWeightAtGrams(String serving) {
        return serving != null && serving.matches("\\s*[\\d.,]+\\s*[gG]\\s*");
    }

    private String formatWeight(BigDecimal grams) {
        return grams.stripTrailingZeros().toPlainString().replace(".", ",") + " g";
    }

    /**
     * Rounds while preserving the order of magnitude — the same rule as the
     * screen. Printing "0 g" where there is 0.23 g of fat would assert
     * absence, which is different from "little".
     */
    private String round(BigDecimal value) {
        int places = value.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0
                : value.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
        return value.setScale(places, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }
}
