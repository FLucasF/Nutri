package br.com.nutriplan.labtest.service;

import br.com.nutriplan.labtest.domain.LabtestOrder;
import br.com.nutriplan.labtest.domain.OrderedParameter;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.shared.error.BusinessRuleException;
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * O pedido de exames em papel.
 *
 * O cliente descreve o que espera: escolhe os painéis, tira o que não quer, e
 * o que ficou ligado sai na folha, separado pelo painel de que veio. A folha
 * é do paciente e vai para o laboratório, então diz de quem é, de quem veio
 * e quando, e termina com a linha de assinatura do profissional.
 */
@Component
public class LabtestOrderPdfGenerator {

    private static final Color PITCH = new Color(0x13, 0x1c, 0x18);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);

    private static final Font TITLE = source(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = source(10, Font.NORMAL, MEAN_INK);
    private static final Font SECTION = source(9, Font.BOLD, MEAN_INK);
    private static final Font ITEM = source(11, Font.NORMAL, PITCH);
    private static final Font SMALL = source(8.5f, Font.NORMAL, MEAN_INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static Font source(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    public byte[] generate(Patient patient, LabtestOrder order,
                           String professionalName, String crn, String practiceName) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 54);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            if (StringUtils.hasText(practiceName)) {
                var practice = new Paragraph(practiceName, SMALL);
                practice.setSpacingAfter(10f);
                document.add(practice);
            }

            var title = new Paragraph("Pedido de exames", TITLE);
            title.setSpacingAfter(2f);
            document.add(title);

            var who = new StringBuilder(patient.getName());
            if (StringUtils.hasText(patient.getCpf())) {
                who.append(" · CPF ").append(patient.getCpf());
            }
            who.append(" · ").append(order.getDate().format(DATE));
            var subtitle = new Paragraph(who.toString(), SUBTITLE);
            subtitle.setSpacingAfter(18f);
            document.add(subtitle);

            Map<String, List<OrderedParameter>> groups = groupsOf(order);
            if (groups.isEmpty()) {
                document.add(new Paragraph("Nenhum exame ligado neste pedido.", ITEM));
            }
            for (var entry : groups.entrySet()) {
                var section = new Paragraph(entry.getKey().toUpperCase(), SECTION);
                section.setSpacingBefore(8f);
                section.setSpacingAfter(3f);
                document.add(section);
                document.add(new Chunk(new LineSeparator(0.6f, 100, ROW, Element.ALIGN_CENTER, -1)));

                var table = new PdfPTable(1);
                table.setWidthPercentage(100);
                table.setSpacingBefore(4f);
                for (OrderedParameter item : entry.getValue()) {
                    var cell = new PdfPCell(new Phrase("•  " + item.getParameter().getName(), ITEM));
                    cell.setBorder(0);
                    cell.setPaddingTop(3f);
                    cell.setPaddingBottom(3f);
                    table.addCell(cell);
                }
                document.add(table);
            }

            if (StringUtils.hasText(order.getNotes())) {
                var notesTitle = new Paragraph("OBSERVAÇÕES", SECTION);
                notesTitle.setSpacingBefore(14f);
                notesTitle.setSpacingAfter(3f);
                document.add(notesTitle);
                document.add(new Paragraph(order.getNotes(), ITEM));
            }

            var signature = new Paragraph();
            signature.setSpacingBefore(56f);
            signature.setAlignment(Element.ALIGN_CENTER);
            signature.add(new Chunk("______________________________________\n", SUBTITLE));
            var line = new StringBuilder();
            if (StringUtils.hasText(professionalName)) {
                line.append(professionalName);
            }
            if (StringUtils.hasText(crn)) {
                line.append(line.length() > 0 ? " · " : "")
                        .append(br.com.nutriplan.shared.util.CrnText.of(crn));
            }
            if (line.length() == 0) {
                line.append("Nutricionista responsável");
            }
            signature.add(new Chunk(line.toString(), SUBTITLE));
            document.add(signature);

            document.close();
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível gerar o PDF do pedido de exames.");
        }
        return out.toByteArray();
    }

    /**
     * Os exames ligados, pelo painel de que vieram, na ordem do pedido. O que
     * foi marcado a mão fica sob o grupo do próprio exame — "Outros" quando
     * nem isso há.
     */
    private Map<String, List<OrderedParameter>> groupsOf(LabtestOrder order) {
        var groups = new LinkedHashMap<String, List<OrderedParameter>>();
        for (OrderedParameter item : order.getParameters()) {
            if (!item.isActive()) {
                continue;
            }
            String key = StringUtils.hasText(item.getPanelName())
                    ? item.getPanelName()
                    : StringUtils.hasText(item.getParameter().getGroup())
                            ? item.getParameter().getGroup()
                            : "Outros";
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(item);
        }
        return groups;
    }
}
