package br.com.nutriplan.anthropometry.service;

import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Uma grandeza desenhada no tempo, em traço vetorial do próprio PDF.
 *
 * Serve ao relatório de evolução e ao relatório de uma avaliação. São linhas
 * num eixo: trazer uma biblioteca de gráficos para isso somaria uma
 * dependência e um rasterizador, e o vetor imprime melhor que a imagem.
 *
 * Uma faixa de referência opcional — a faixa de peso saudável — entra como
 * um fundo claro entre dois valores, e a escala se abre para contê-la: o
 * leitor tem de ver onde está a linha em relação a ela, não só a linha.
 */
final class PdfLineChart {

    private static final Color PITCH = new Color(0x22, 0x28, 0x24);
    private static final Color MEAN_INK = new Color(0x56, 0x61, 0x59);
    private static final Color LINE = new Color(0x8c, 0x2f, 0x51);
    private static final Color GRID = new Color(0xdf, 0xe3, 0xdd);
    private static final Color BAND = new Color(0xe6, 0xf2, 0xeb);
    private static final Color BAND_INK = new Color(0x2f, 0x6b, 0x4f);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", 10, Font.BOLD, PITCH);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", 7, Font.NORMAL, MEAN_INK);

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM/uu");

    /** Um ponto medido. */
    record Point(LocalDate date, double value) {}

    /** Uma faixa de referência: o fundo claro entre dois valores, com o nome dela. */
    record Band(double low, double high, String label) {}

    private PdfLineChart() {
    }

    /**
     * O gráfico, ou nulo com menos de dois pontos: um ponto solto não é uma
     * evolução, e um gráfico de um ponto só sugere uma linha que não existe.
     */
    static Image draw(PdfWriter writer, String heading, List<Point> points, float width, Band band)
            throws Exception {
        if (points.size() < 2) {
            return null;
        }

        float height = 132f;
        PdfTemplate canvas = writer.getDirectContent().createTemplate(width, height);

        float left = 42f;
        float right = width - 8f;
        float bottom = 26f;
        float top = height - 22f;

        double lowest = Double.MAX_VALUE;
        double highest = -Double.MAX_VALUE;
        for (Point point : points) {
            lowest = Math.min(lowest, point.value());
            highest = Math.max(highest, point.value());
        }
        if (band != null) {
            lowest = Math.min(lowest, band.low());
            highest = Math.max(highest, band.high());
        }
        // Uma faixa mínima evita que uma variação de 200 g ocupe o gráfico
        // inteiro e pareça um despencar.
        double minimumRange = Math.max(Math.abs(highest) * 0.04, 1);
        if (highest - lowest < minimumRange) {
            double middle = (highest + lowest) / 2;
            lowest = middle - minimumRange / 2;
            highest = middle + minimumRange / 2;
        }
        // O eixo em degraus limpos — 40, 60, 80 —, e não nos valores quebrados
        // que a margem produziria.
        double step = niceStep(highest - lowest, 3);
        double floor = Math.floor(lowest / step) * step;
        double ceiling = Math.ceil(highest / step) * step;
        int steps = (int) Math.round((ceiling - floor) / step);

        BaseFont titleFont = TITLE.getBaseFont();
        BaseFont smallFont = SMALL.getBaseFont();

        canvas.beginText();
        canvas.setFontAndSize(titleFont, 10f);
        canvas.setColorFill(PITCH);
        canvas.setTextMatrix(0, height - 12f);
        canvas.showText(heading);
        canvas.endText();

        if (band != null) {
            float yLow = y(band.low(), floor, ceiling, bottom, top);
            float yHigh = y(band.high(), floor, ceiling, bottom, top);
            canvas.setColorFill(BAND);
            canvas.rectangle(left, yLow, right - left, yHigh - yLow);
            canvas.fill();

            canvas.beginText();
            canvas.setFontAndSize(smallFont, 6.5f);
            canvas.setColorFill(BAND_INK);
            canvas.setTextMatrix(left + 4f, yHigh - 8f);
            canvas.showText(band.label());
            canvas.endText();
        }

        // Uma linha de grade por degrau, com o valor à esquerda.
        canvas.setLineWidth(0.5f);
        for (int i = 0; i <= steps; i++) {
            double value = floor + step * i;
            float gy = y(value, floor, ceiling, bottom, top);
            canvas.setColorStroke(GRID);
            canvas.moveTo(left, gy);
            canvas.lineTo(right, gy);
            canvas.stroke();

            canvas.beginText();
            canvas.setFontAndSize(smallFont, 7f);
            canvas.setColorFill(MEAN_INK);
            canvas.setTextMatrix(2f, gy - 2f);
            canvas.showText(number(value));
            canvas.endText();
        }

        float spacing = (right - left) / (points.size() - 1);

        canvas.setLineWidth(1.4f);
        canvas.setColorStroke(LINE);
        for (int i = 0; i < points.size(); i++) {
            float x = left + spacing * i;
            float py = y(points.get(i).value(), floor, ceiling, bottom, top);
            if (i == 0) {
                canvas.moveTo(x, py);
            } else {
                canvas.lineTo(x, py);
            }
        }
        canvas.stroke();

        // Os pontos e os rótulos num segundo passe: desenhá-los junto com a
        // linha interromperia o traço a cada marca. O valor sai só na primeira
        // e na última medida; as do meio estão na tabela.
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            float x = left + spacing * i;
            float py = y(point.value(), floor, ceiling, bottom, top);

            canvas.setColorFill(LINE);
            canvas.circle(x, py, 2.4f);
            canvas.fill();

            if (i == 0 || i == points.size() - 1) {
                canvas.beginText();
                canvas.setFontAndSize(smallFont, 7f);
                canvas.setColorFill(PITCH);
                canvas.setTextMatrix(inside(x - 8f, 22f, width), py + 6f);
                canvas.showText(number(point.value()));
                canvas.endText();
            }

            if (points.size() <= 8 || i == 0 || i == points.size() - 1) {
                canvas.beginText();
                canvas.setFontAndSize(smallFont, 6.5f);
                canvas.setColorFill(MEAN_INK);
                canvas.setTextMatrix(inside(x - 12f, 32f, width), 10f);
                canvas.showText(point.date().format(SHORT));
                canvas.endText();
            }
        }

        Image image = Image.getInstance(canvas);
        image.setSpacingBefore(4f);
        image.setSpacingAfter(10f);
        return image;
    }

    /** Um degrau de 1, 2, 2,5 ou 5 vezes uma potência de dez. */
    private static double niceStep(double range, int ticks) {
        double raw = range / ticks;
        double power = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / power;
        double nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
        return nice * power;
    }

    private static float y(double value, double floor, double ceiling, float bottom, float top) {
        return bottom + (float) ((value - floor) / (ceiling - floor) * (top - bottom));
    }

    /**
     * Mantém um rótulo dentro do desenho: o último ponto fica encostado na
     * margem direita, e o rótulo centrado nele sairia pela borda.
     */
    private static float inside(float x, float labelWidth, float canvasWidth) {
        return Math.max(0f, Math.min(x, canvasWidth - labelWidth));
    }

    static String number(double value) {
        String text = String.format(Locale.of("pt", "BR"), "%.1f", value);
        return text.endsWith(",0") ? text.substring(0, text.length() - 2) : text;
    }
}
