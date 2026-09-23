package br.com.nutriplan.anamnesis.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import br.com.nutriplan.anamnesis.dto.AnamnesisDtos;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.richtext.RichTextPdf;
import lombok.RequiredArgsConstructor;

/**
 * The anamnesis on paper.
 *
 * The client asked for a PDF button on every record in the listing, and also
 * for a button inside the plan editor that opens this sheet in another tab —
 * so this file is read while a menu is being built, not only when something is
 * handed to the patient. That is why the declared fields come first, in a
 * block: they are what gets looked up in a hurry.
 */
@Component
@RequiredArgsConstructor
public class AnamnesisPdfGenerator {

    private static final Color PITCH = new Color(0x13, 0x1c, 0x18);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);
    private static final Color HEADER = new Color(0xf1, 0xf3, 0xef);

    private static final Font TITLE = source(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = source(10, Font.NORMAL, MEAN_INK);
    private static final Font LABEL = source(8, Font.BOLD, MEAN_INK);
    private static final Font VALUE = source(11, Font.NORMAL, PITCH);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final ObjectMapper mapper;

    private static Font source(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    public byte[] generate(AnamnesisDtos.AnamnesisResponse anamnesis) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // O nome do paciente é o título, e o da anamnese vira legenda. A
            // folha sai da tela e circula sozinha: impressa, anexada, guardada
            // numa pasta. O que quem pega precisa ler primeiro é de quem ela é,
            // não que ela se chama "Anamnese inicial" — isso vale para todas.
            var title = new Paragraph(
                    anamnesis.patientName() == null ? anamnesis.name() : anamnesis.patientName(),
                    TITLE);
            title.setSpacingAfter(2f);
            document.add(title);

            var legend = new StringBuilder(anamnesis.name());
            legend.append(" · ").append(anamnesis.date().format(DATE));
            var subtitle = new Paragraph(legend.toString(), SUBTITLE);
            subtitle.setSpacingAfter(16f);
            document.add(subtitle);

            if (!anamnesis.values().isEmpty()) {
                document.add(highlights(anamnesis));
            }

            for (Element element : RichTextPdf.render(anamnesis.body(), mapper)) {
                document.add(element);
            }

            document.close();
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível gerar o PDF da anamnese.");
        }
        return out.toByteArray();
    }

    /**
     * The declared fields, two per row.
     *
     * The label goes above the value and in a smaller size: what is being
     * looked for is the value, and the label is there to say which one it is.
     */
    private PdfPTable highlights(AnamnesisDtos.AnamnesisResponse anamnesis) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(18f);

        for (AnamnesisDtos.ValueResponse value : anamnesis.values()) {
            var cell = new PdfPCell();
            cell.setBorderColor(ROW);
            cell.setBackgroundColor(HEADER);
            cell.setPadding(7f);

            var label = new Paragraph(value.label().toUpperCase(), LABEL);
            label.setSpacingAfter(2f);
            cell.addElement(label);
            cell.addElement(new Phrase(value.value() == null ? "—" : value.value(), VALUE));
            table.addCell(cell);
        }

        // An odd count leaves a hole; an empty cell with no border closes the
        // row without drawing a box around nothing.
        if (anamnesis.values().size() % 2 == 1) {
            var filler = new PdfPCell(new Phrase(""));
            filler.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
            table.addCell(filler);
        }
        return table;
    }
}
