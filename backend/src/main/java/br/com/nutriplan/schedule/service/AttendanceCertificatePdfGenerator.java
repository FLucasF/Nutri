package br.com.nutriplan.schedule.service;

import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.shared.error.BusinessRuleException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * O atestado de comparecimento em papel.
 *
 * Diz quem veio, quando e por quanto tempo, e quem atesta. Nada além disso:
 * o paciente leva a folha para o trabalho ou para a escola, e o que ela
 * precisa provar é a presença — não o motivo da consulta, que é sigilo.
 */
@Component
public class AttendanceCertificatePdfGenerator {

    private static final Color PITCH = new Color(0x13, 0x1c, 0x18);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);

    private static final Font TITLE = source(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = source(10, Font.NORMAL, MEAN_INK);
    private static final Font BODY = source(11.5f, Font.NORMAL, PITCH);
    private static final Font SMALL = source(8.5f, Font.NORMAL, MEAN_INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private static Font source(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    public byte[] generate(Patient patient, Appointment appointment,
                           String professionalName, String crn, String practiceName) {
        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 64, 64, 64, 64);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            if (StringUtils.hasText(practiceName)) {
                var practice = new Paragraph(practiceName, SMALL);
                practice.setSpacingAfter(10f);
                document.add(practice);
            }

            var title = new Paragraph("Atestado de comparecimento", TITLE);
            title.setSpacingAfter(28f);
            document.add(title);

            var text = new StringBuilder("Atesto, para os devidos fins, que ")
                    .append(patient.getName());
            if (StringUtils.hasText(patient.getCpf())) {
                text.append(", CPF ").append(patient.getCpf());
            }
            text.append(", compareceu a atendimento nutricional")
                    .append(StringUtils.hasText(practiceName) ? " em " + practiceName : " neste consultório")
                    .append(" no dia ").append(appointment.getStart().format(DATE))
                    .append(", no horário das ").append(appointment.getStart().format(HOUR))
                    .append(" às ").append(appointment.getEnd().format(HOUR))
                    .append(".");
            var body = new Paragraph(text.toString(), BODY);
            body.setLeading(19f);
            body.setAlignment(Element.ALIGN_JUSTIFIED);
            body.setSpacingAfter(14f);
            document.add(body);

            var nature = new Paragraph("Natureza do atendimento: "
                    + appointment.getType().getDescription().toLowerCase() + ".", SUBTITLE);
            nature.setSpacingAfter(36f);
            document.add(nature);

            var issued = new Paragraph("Emitido em " + LocalDate.now().format(DATE) + ".", SUBTITLE);
            issued.setSpacingAfter(64f);
            document.add(issued);

            var signature = new Paragraph();
            signature.setAlignment(Element.ALIGN_CENTER);
            signature.add(new Chunk("______________________________________\n", SUBTITLE));
            var line = new StringBuilder();
            if (StringUtils.hasText(professionalName)) {
                line.append(professionalName);
            }
            if (StringUtils.hasText(crn)) {
                line.append(line.length() > 0 ? " · CRN " : "CRN ").append(crn);
            }
            if (line.length() == 0) {
                line.append("Nutricionista responsável");
            }
            signature.add(new Chunk(line.toString(), SUBTITLE));
            document.add(signature);

            document.close();
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível gerar o PDF do atestado.");
        }
        return out.toByteArray();
    }
}
