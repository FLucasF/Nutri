package br.com.nutriplan.anthropometry.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

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

import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import br.com.nutriplan.anthropometry.domain.AssessmentCircumference;
import br.com.nutriplan.shared.error.BusinessRuleException;

/**
 * O relatório de evolução antropométrica.
 *
 * "Adicionar funcionalidade de gerar relatórios com gráficos na antropometria."
 *
 * A folha responde uma pergunta que a tabela na tela não responde bem: o que
 * mudou, e em que direção. Uma coluna de números diz que o peso foi de 88,4
 * para 80,3; a linha diz que a queda foi constante, e é isso que se mostra ao
 * paciente na consulta.
 *
 * Os gráficos são desenhados à mão, com as primitivas do próprio PDF, em vez de
 * virem de uma biblioteca de gráficos. São três linhas num eixo: trazer JFreeChart
 * para isso somaria uma dependência e um rasterizador — e o traço vetorial
 * imprime melhor do que a imagem que ela geraria.
 */
@Service
public class AnthropometryReportGenerator {

    private static final Color PITCH = new Color(0x22, 0x28, 0x24);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);
    private static final Color HEADER = new Color(0xf1, 0xf3, 0xef);
    private static final Color BEETROOT = new Color(0x8c, 0x2f, 0x51);

    private static final Font TITLE = font(20, Font.BOLD, PITCH);
    private static final Font SUBTITLE = font(10, Font.NORMAL, MEAN_INK);
    private static final Font SECTION = font(12, Font.BOLD, PITCH);
    private static final Font HEAD = font(8, Font.BOLD, MEAN_INK);
    private static final Font SMALL = font(7, Font.NORMAL, MEAN_INK);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM/uu");

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, color);
    }

    /** Uma grandeza acompanhada ao longo do tempo. */
    private record Track(String title, String unit,
                         Function<AnthropometricAssessment, BigDecimal> reading) {}

    private static final List<Track> TRACKS = List.of(
            new Track("Peso", "kg", AnthropometricAssessment::getWeightKg),
            new Track("IMC", "kg/m²", AnthropometricAssessment::getBmi),
            new Track("Gordura corporal", "%", AnthropometricAssessment::getPercentageFat));

    public byte[] generate(String patientName, List<AnthropometricAssessment> series) {
        if (series.size() < 2) {
            throw new BusinessRuleException(
                    "A evolução precisa de pelo menos duas avaliações para comparar.");
        }

        var out = new ByteArrayOutputStream();
        var document = new Document(PageSize.A4, 48, 48, 48, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            AnthropometricAssessment first = series.get(0);
            AnthropometricAssessment last = series.get(series.size() - 1);

            var title = new Paragraph(patientName == null ? "Evolução" : patientName, TITLE);
            title.setSpacingAfter(2f);
            document.add(title);

            var subtitle = new Paragraph(
                    "Evolução antropométrica · " + series.size() + " avaliações · "
                            + first.getDate().format(DATE) + " a " + last.getDate().format(DATE),
                    SUBTITLE);
            subtitle.setSpacingAfter(18f);
            document.add(subtitle);

            // A faixa de peso pela altura da última avaliação, onde o cliente
            // pediu para vê-la: "nos relatórios".
            var range = br.com.nutriplan.anthropometry.domain.HealthyWeightRange.of(last.getHeightCm());
            if (range != null) {
                var healthy = new Paragraph(
                        "Faixa de peso saudável: " + number(range.minimumKg()) + " a "
                                + number(range.maximumKg()) + " kg (IMC de "
                                + number(range.bmiMinimum()) + " a " + number(range.bmiMaximum())
                                + " kg/m², para " + number(last.getHeightCm()) + " cm).",
                        font(9, Font.NORMAL, PITCH));
                healthy.setSpacingAfter(14f);
                document.add(healthy);
            }

            float width = document.right() - document.left();
            for (Track track : TRACKS) {
                Element chart = chart(writer, track, series, width);
                if (chart != null) {
                    document.add(chart);
                }
            }

            document.add(section("Dobras cutâneas"));
            document.add(comparison(skinfoldsOf(first), skinfoldsOf(last), first, last, "mm"));

            document.add(section("Circunferências"));
            document.add(comparison(circumferencesOf(first), circumferencesOf(last),
                    first, last, "cm"));

            document.close();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("Não foi possível gerar o relatório de evolução.");
        }
        return out.toByteArray();
    }

    private Paragraph section(String text) {
        var paragraph = new Paragraph(text, SECTION);
        paragraph.setSpacingBefore(14f);
        paragraph.setSpacingAfter(6f);
        return paragraph;
    }

    // ------------------------------------------------------------- gráficos

    /**
     * Uma grandeza desenhada no tempo, pelo mesmo traço do relatório de uma
     * avaliação. O peso ganha a faixa saudável da última altura por trás.
     */
    private Element chart(PdfWriter writer, Track track,
                          List<AnthropometricAssessment> series, float width) throws Exception {
        var points = new ArrayList<PdfLineChart.Point>();
        for (AnthropometricAssessment assessment : series) {
            BigDecimal value = track.reading().apply(assessment);
            if (value != null) {
                points.add(new PdfLineChart.Point(assessment.getDate(), value.doubleValue()));
            }
        }
        PdfLineChart.Band band = null;
        if ("kg".equals(track.unit()) && !series.isEmpty()) {
            var range = br.com.nutriplan.anthropometry.domain.HealthyWeightRange.of(
                    series.get(series.size() - 1).getHeightCm());
            if (range != null) {
                band = new PdfLineChart.Band(range.minimumKg().doubleValue(),
                        range.maximumKg().doubleValue(), "faixa de peso saudável");
            }
        }
        return PdfLineChart.draw(writer, track.title() + " (" + track.unit() + ")", points, width, band);
    }

    // --------------------------------------------------------------- tabelas

    /**
     * Primeira medida, última e a diferença.
     *
     * Só entram as linhas que existem nas duas pontas. Comparar contra uma
     * ausência produziria uma diferença que parece perda de dez centímetros
     * quando o que houve foi uma dobra não medida daquela vez.
     */
    private PdfPTable comparison(Map<String, BigDecimal> first, Map<String, BigDecimal> last,
                                 AnthropometricAssessment firstAssessment,
                                 AnthropometricAssessment lastAssessment,
                                 String unit) {
        var table = new PdfPTable(new float[] {3f, 1.3f, 1.3f, 1.3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);

        header(table, "Local", Element.ALIGN_LEFT);
        header(table, firstAssessment.getDate().format(SHORT), Element.ALIGN_RIGHT);
        header(table, lastAssessment.getDate().format(SHORT), Element.ALIGN_RIGHT);
        header(table, "Diferença", Element.ALIGN_RIGHT);

        boolean any = false;
        for (Map.Entry<String, BigDecimal> entry : first.entrySet()) {
            BigDecimal end = last.get(entry.getKey());
            if (end == null) {
                continue;
            }
            any = true;
            BigDecimal difference = end.subtract(entry.getValue());
            cell(table, entry.getKey(), Element.ALIGN_LEFT, PITCH);
            cell(table, number(entry.getValue()) + " " + unit, Element.ALIGN_RIGHT, PITCH);
            cell(table, number(end) + " " + unit, Element.ALIGN_RIGHT, PITCH);
            cell(table, signed(difference) + " " + unit, Element.ALIGN_RIGHT,
                    difference.signum() == 0 ? MEAN_INK : BEETROOT);
        }

        if (!any) {
            var cell = new PdfPCell(new Phrase(
                    "Sem medidas presentes nas duas avaliações para comparar.", SMALL));
            cell.setColspan(4);
            cell.setBorderColor(ROW);
            cell.setPadding(6f);
            table.addCell(cell);
        }
        return table;
    }

    /**
     * Uma celula de cabecalho.
     *
     * O alinhamento vem de fora, e nao de contar as celulas ja adicionadas: a
     * contagem de linhas da tabela so avanca quando a linha fecha, entao
     * durante o cabecalho ela vale zero para todas as quatro — e todas sairiam
     * alinhadas como a primeira.
     */
    private void header(PdfPTable table, String text, int alignment) {
        var cell = new PdfPCell(new Phrase(text.toUpperCase(), HEAD));
        cell.setBackgroundColor(HEADER);
        cell.setBorderColor(ROW);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void cell(PdfPTable table, String text, int alignment, Color color) {
        var cell = new PdfPCell(new Phrase(text, font(9, Font.NORMAL, color)));
        cell.setBorderColor(ROW);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    // ------------------------------------------------------------ leituras

    private Map<String, BigDecimal> skinfoldsOf(AnthropometricAssessment assessment) {
        var output = new LinkedHashMap<String, BigDecimal>();
        assessment.skinfoldsMeasures().forEach((skinfold, value) -> {
            if (value != null) {
                output.put(skinfold.getDescription(), BigDecimal.valueOf(value));
            }
        });
        return output;
    }

    private Map<String, BigDecimal> circumferencesOf(AnthropometricAssessment assessment) {
        var output = new LinkedHashMap<String, BigDecimal>();
        for (AssessmentCircumference circumference : assessment.getCircumferences()) {
            if (circumference.getValueCm() == null) {
                continue;
            }
            // O lado entra no rótulo porque sete dos treze locais são medidos
            // dos dois, e "Braço relaxado" sozinho casaria o direito de março
            // com o esquerdo de setembro.
            String label = circumference.getSite().getDescription()
                    + (circumference.getSide().name().equals("SINGLE")
                            ? ""
                            : " (" + circumference.getSide().getDescription().toLowerCase() + ")");
            output.put(label, circumference.getValueCm());
        }
        return output;
    }

    // --------------------------------------------------------------- número

    private String number(BigDecimal value) {
        return number(value.doubleValue());
    }

    private String number(double value) {
        String text = String.format(java.util.Locale.of("pt", "BR"), "%.1f", value);
        return text.endsWith(",0") ? text.substring(0, text.length() - 2) : text;
    }

    private String signed(BigDecimal value) {
        String text = number(value);
        return value.signum() > 0 ? "+" + text : text;
    }
}
