package br.com.nutriplan.shared.richtext;

import java.awt.Color;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.ListItem;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

/**
 * The rich text document, drawn on paper.
 *
 * This is the other half of the promise the format makes. The editor renders a
 * 12 as {@code 12pt} of CSS; here a 12 becomes a 12-point font. Both are
 * absolute and both come from the same closed set, so the size the
 * professional chose on screen is the size that leaves the printer. That is
 * the whole reason the format stores a number instead of a CSS string — the
 * complaint it answers is having to fix the size again in every plan.
 */
public final class RichTextPdf {

    private static final Color INK = new Color(0x16, 0x21, 0x1c);
    private static final Color ROW = new Color(0xc4, 0xca, 0xc1);
    private static final Color HEADER = new Color(0xf1, 0xf3, 0xef);

    /** The size of text that declares none. Matches FONT_SIZE_DEFAULT on screen. */
    private static final float SIZE_DEFAULT = 11f;

    private RichTextPdf() {
    }

    /**
     * The document as a list of elements, ready to add to an open document.
     *
     * Returns empty for a blank or unreadable body: a PDF missing a paragraph
     * is better than a request that fails while someone is trying to print.
     */
    public static java.util.List<Element> render(String json, ObjectMapper mapper) {
        return render(json, mapper, SIZE_DEFAULT);
    }

    /**
     * O mesmo, com outro tamanho para o texto que nao declara o proprio.
     *
     * Serve o que e impresso dentro de outra coisa — a observacao de um
     * alimento, dentro da celula da refeicao. O tamanho escrito no editor
     * continua valendo: este aqui so decide o do texto que nao escolheu.
     */
    public static java.util.List<Element> render(String json, ObjectMapper mapper,
                                                 float defaultSize) {
        var out = new ArrayList<Element>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            out.add(new Paragraph(json, font(defaultSize, 0)));
            return out;
        }
        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            return out;
        }
        for (JsonNode block : content) {
            Element element = renderBlock(block, defaultSize);
            if (element != null) {
                out.add(element);
            }
        }
        return out;
    }

    private static Element renderBlock(JsonNode node, float defaultSize) {
        String type = text(node, "type");
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "paragraph" -> renderParagraph(node, defaultSize);
            case "bulletList", "orderedList" ->
                    renderList(node, "orderedList".equals(type), defaultSize);
            case "table" -> renderTable(node, defaultSize);
            default -> null;
        };
    }

    private static Paragraph renderParagraph(JsonNode node, float defaultSize) {
        var paragraph = new Paragraph();
        paragraph.setLeading(0, 1.25f);
        paragraph.setSpacingAfter(4f);
        paragraph.setAlignment(alignment(node));

        JsonNode content = node.get("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            // A blank paragraph is a line the author left on purpose.
            paragraph.add(new Chunk(" ", font(defaultSize, 0)));
            return paragraph;
        }
        for (JsonNode piece : content) {
            paragraph.add(renderText(piece, defaultSize));
        }
        return paragraph;
    }

    private static com.lowagie.text.List renderList(JsonNode node, boolean numbered,
                                                    float defaultSize) {
        var list = new com.lowagie.text.List(numbered, 12f);
        if (!numbered) {
            list.setListSymbol(new Chunk("•  ", font(defaultSize, 0)));
        }
        JsonNode content = node.get("content");
        if (content == null || !content.isArray()) {
            return list;
        }
        for (JsonNode item : content) {
            var listItem = new ListItem();
            JsonNode children = item.get("content");
            if (children != null && children.isArray()) {
                for (JsonNode child : children) {
                    Element element = renderBlock(child, defaultSize);
                    if (element != null) {
                        listItem.add(element);
                    }
                }
            }
            list.add(listItem);
        }
        return list;
    }

    private static PdfPTable renderTable(JsonNode node, float defaultSize) {
        JsonNode rows = node.get("content");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return new PdfPTable(1);
        }
        int columns = widestRow(rows);
        var table = new PdfPTable(Math.max(columns, 1));
        table.setWidthPercentage(100);
        table.setSpacingBefore(6f);
        table.setSpacingAfter(8f);

        for (JsonNode row : rows) {
            JsonNode cells = row.get("content");
            if (cells == null || !cells.isArray()) {
                continue;
            }
            for (JsonNode cell : cells) {
                table.addCell(renderCell(cell, defaultSize));
            }
        }
        return table;
    }

    private static PdfPCell renderCell(JsonNode node, float defaultSize) {
        var cell = new PdfPCell();
        cell.setBorderColor(ROW);
        cell.setPadding(4f);
        cell.setColspan(span(node, "colspan"));
        cell.setRowspan(span(node, "rowspan"));
        if ("tableHeader".equals(text(node, "type"))) {
            cell.setBackgroundColor(HEADER);
        }
        JsonNode content = node.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode paragraph : content) {
                Element element = renderBlock(paragraph, defaultSize);
                if (element != null) {
                    cell.addElement(element);
                }
            }
        }
        return cell;
    }

    private static Chunk renderText(JsonNode node, float defaultSize) {
        String value = text(node, "text");
        if (value == null) {
            return new Chunk("");
        }
        float size = defaultSize;
        int style = 0;
        boolean strike = false;

        JsonNode marks = node.get("marks");
        if (marks != null && marks.isArray()) {
            for (JsonNode mark : marks) {
                String type = text(mark, "type");
                if (type == null) {
                    continue;
                }
                switch (type) {
                    case "bold" -> style |= Font.BOLD;
                    case "italic" -> style |= Font.ITALIC;
                    case "underline" -> style |= Font.UNDERLINE;
                    case "strike" -> strike = true;
                    case "textStyle" -> {
                        JsonNode attrs = mark.get("attrs");
                        JsonNode fontSize = attrs == null ? null : attrs.get("fontSize");
                        if (fontSize != null && fontSize.isInt()
                                && RichTextDocument.FONT_SIZES.contains(fontSize.asInt())) {
                            size = fontSize.asInt();
                        }
                    }
                    default -> { }
                }
            }
        }
        // OpenPDF draws the strikethrough as a style, but combining it with the
        // underline bit loses one of the two. Drawn apart, both survive.
        var chunk = new Chunk(value, font(size, style));
        if (strike) {
            chunk.setUnderline(0.06f * size, 0.30f * size);
        }
        return chunk;
    }

    // ------------------------------------------------------------------ apoio

    private static int widestRow(JsonNode rows) {
        int widest = 1;
        for (JsonNode row : rows) {
            JsonNode cells = row.get("content");
            if (cells == null || !cells.isArray()) {
                continue;
            }
            int width = 0;
            for (JsonNode cell : cells) {
                width += span(cell, "colspan");
            }
            widest = Math.max(widest, width);
        }
        return widest;
    }

    private static int span(JsonNode node, String name) {
        JsonNode attrs = node.get("attrs");
        JsonNode value = attrs == null ? null : attrs.get(name);
        return value != null && value.isInt() && value.asInt() >= 1 ? value.asInt() : 1;
    }

    private static int alignment(JsonNode node) {
        JsonNode attrs = node.get("attrs");
        String align = attrs == null ? null : text(attrs, "textAlign");
        if (align == null) {
            return Element.ALIGN_LEFT;
        }
        return switch (align) {
            case "center" -> Element.ALIGN_CENTER;
            case "right" -> Element.ALIGN_RIGHT;
            case "justify" -> Element.ALIGN_JUSTIFIED;
            default -> Element.ALIGN_LEFT;
        };
    }

    private static Font font(float size, int style) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", size, style, INK);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }
}
