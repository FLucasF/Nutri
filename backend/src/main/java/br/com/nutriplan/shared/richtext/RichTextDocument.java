package br.com.nutriplan.shared.richtext;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import br.com.nutriplan.shared.error.BusinessRuleException;

/**
 * The rich text document, checked at the edge.
 *
 * The browser writes this format and the browser could write anything: what
 * arrives here is a string, whatever the TypeScript type says upstream. The
 * editor's schema lives in the client and does not travel with the payload, so
 * without this class the model would be closed on one side of the wire and open
 * on the other — and the open side is the one that owns the database.
 *
 * What it accepts mirrors {@code frontend/src/components/RichText/document.ts}:
 * paragraphs, bullet and ordered lists, tables with merged cells, and the marks
 * bold, italic, underline, strike and font size. Anything else is refused
 * rather than stripped, because clinical text that gets silently "fixed" is
 * worse than text that gets rejected — nobody finds out about the first.
 */
public final class RichTextDocument {

    /** Font sizes in points. Closed, and the same list the editor offers. */
    public static final Set<Integer> FONT_SIZES = Set.of(8, 9, 10, 11, 12, 14, 16, 18, 24);

    private static final Set<String> ALIGNMENTS = Set.of("left", "center", "right", "justify");
    private static final Set<String> SIMPLE_MARKS = Set.of("bold", "italic", "underline", "strike");

    /** Guards against a document deep enough to blow the stack while walking it. */
    private static final int DEPTH_MAX = 30;

    /** Guards against a payload that is valid and still absurd. */
    private static final int NODES_MAX = 20_000;

    private final String json;

    private RichTextDocument(String json) {
        this.json = json;
    }

    /** The document as it goes to the column, or {@code null} when there is none. */
    public String json() {
        return json;
    }

    /**
     * Validates the incoming text and hands back what may be persisted.
     *
     * Null and blank mean "no document" and pass through: an empty field is not
     * an invalid one.
     */
    public static RichTextDocument of(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return new RichTextDocument(null);
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            throw new BusinessRuleException("O texto formatado chegou ilegível.");
        }
        var counter = new int[] { 0 };
        readDoc(root, counter);
        return new RichTextDocument(raw);
    }

    /**
     * Accepts either a document or plain text, and always returns a document.
     *
     * It exists for the fields that held plain text before this format did:
     * a meal's notes, written a year ago, is a sentence and not JSON. Refusing
     * it would make an old plan unsaveable; storing it as-is would leave the
     * column holding two different things.
     *
     * Wrapping converts, and conversion is visible — the text becomes a
     * one-paragraph document and keeps saying what it said.
     */
    public static RichTextDocument ofTextOrDocument(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return new RichTextDocument(null);
        }
        String trimmed = raw.strip();
        boolean looksLikeDocument = trimmed.startsWith("{") || trimmed.startsWith("[");
        if (looksLikeDocument) {
            return of(raw, mapper);
        }
        var doc = mapper.createObjectNode();
        doc.put("type", "doc");
        var content = doc.putArray("content");
        for (String line : raw.split("\r?\n")) {
            var paragraph = content.addObject();
            paragraph.put("type", "paragraph");
            if (!line.isEmpty()) {
                var text = paragraph.putArray("content").addObject();
                text.put("type", "text");
                text.put("text", line);
            }
        }
        return new RichTextDocument(doc.toString());
    }

    /**
     * The text without formatting.
     *
     * Serves listing, search and short summaries. It is derived, never stored:
     * a copy of the text that can drift from the original is a second source of
     * truth for the same sentence.
     */
    public static String plainText(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            return raw;
        }
        var out = new StringBuilder();
        collectText(root, out);
        return out.toString().strip();
    }

    // --------------------------------------------------------------- validation

    private static void readDoc(JsonNode node, int[] counter) {
        require(node.isObject() && "doc".equals(text(node, "type")),
                "O texto formatado precisa começar por um documento.");
        for (JsonNode block : array(node, "content")) {
            readBlock(block, counter, 1);
        }
    }

    private static void readBlock(JsonNode node, int[] counter, int depth) {
        count(counter, depth);
        String type = text(node, "type");
        if (type == null) {
            throw refused("um bloco sem tipo");
        }
        switch (type) {
            case "paragraph" -> readParagraph(node, counter, depth);
            case "bulletList", "orderedList" -> {
                for (JsonNode item : array(node, "content")) {
                    count(counter, depth);
                    require("listItem".equals(text(item, "type")),
                            "Uma lista só contém itens de lista.");
                    // An item holds paragraphs and may hold another list: that
                    // is the Tab inside a bullet, which is the Word behaviour
                    // the editor offers.
                    for (JsonNode child : array(item, "content")) {
                        readBlock(child, counter, depth + 1);
                    }
                }
            }
            case "table" -> {
                for (JsonNode row : array(node, "content")) {
                    count(counter, depth);
                    require("tableRow".equals(text(row, "type")),
                            "Uma tabela só contém linhas.");
                    for (JsonNode cell : array(row, "content")) {
                        readCell(cell, counter, depth + 1);
                    }
                }
            }
            default -> throw refused(type);
        }
    }

    private static void readCell(JsonNode node, int[] counter, int depth) {
        count(counter, depth);
        String type = text(node, "type");
        require("tableCell".equals(type) || "tableHeader".equals(type),
                "Uma linha de tabela só contém células.");
        JsonNode attrs = node.get("attrs");
        if (attrs != null && attrs.isObject()) {
            readSpan(attrs, "colspan");
            readSpan(attrs, "rowspan");
        }
        for (JsonNode paragraph : array(node, "content")) {
            require("paragraph".equals(text(paragraph, "type")),
                    "Uma célula só contém parágrafos.");
            readParagraph(paragraph, counter, depth + 1);
        }
    }

    private static void readSpan(JsonNode attrs, String name) {
        JsonNode value = attrs.get(name);
        if (value == null || value.isNull()) {
            return;
        }
        require(value.isInt() && value.asInt() >= 1 && value.asInt() <= 50,
                "A mesclagem de células chegou com um tamanho impossível.");
    }

    private static void readParagraph(JsonNode node, int[] counter, int depth) {
        count(counter, depth);
        JsonNode attrs = node.get("attrs");
        if (attrs != null && attrs.isObject()) {
            JsonNode align = attrs.get("textAlign");
            if (align != null && !align.isNull()) {
                require(align.isTextual() && ALIGNMENTS.contains(align.asText()),
                        "Alinhamento desconhecido no texto formatado.");
            }
        }
        for (JsonNode piece : array(node, "content")) {
            count(counter, depth);
            require("text".equals(text(piece, "type")) && piece.hasNonNull("text")
                            && piece.get("text").isTextual(),
                    "Um parágrafo só contém texto.");
            readMarks(piece);
        }
    }

    private static void readMarks(JsonNode piece) {
        for (JsonNode mark : array(piece, "marks")) {
            String type = text(mark, "type");
            if (type == null) {
                throw refused("uma marca sem tipo");
            }
            if (SIMPLE_MARKS.contains(type)) {
                continue;
            }
            if (!"textStyle".equals(type)) {
                throw refused(type);
            }
            JsonNode attrs = mark.get("attrs");
            JsonNode size = attrs == null ? null : attrs.get("fontSize");
            if (size == null || size.isNull()) {
                // A textStyle with nothing to say is how the editor leaves the
                // mark after the size is cleared. It carries no formatting.
                continue;
            }
            require(size.isInt() && FONT_SIZES.contains(size.asInt()),
                    "Tamanho de fonte fora dos valores disponíveis no editor.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void collectText(JsonNode node, StringBuilder out) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectText(child, out);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if ("text".equals(text(node, "type")) && node.hasNonNull("text")) {
            out.append(node.get("text").asText());
        }
        JsonNode content = node.get("content");
        if (content != null) {
            collectText(content, out);
        }
        String type = text(node, "type");
        if ("paragraph".equals(type) || "tableRow".equals(type) || "listItem".equals(type)) {
            out.append('\n');
        }
    }

    private static List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        require(value.isArray(), "O texto formatado chegou com uma lista malformada.");
        var out = new java.util.ArrayList<JsonNode>();
        ((ArrayNode) value).forEach(out::add);
        return out;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static void count(int[] counter, int depth) {
        require(depth <= DEPTH_MAX, "O texto formatado está aninhado demais.");
        counter[0]++;
        require(counter[0] <= NODES_MAX, "O texto formatado é grande demais.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessRuleException(message);
        }
    }

    private static BusinessRuleException refused(String what) {
        return new BusinessRuleException(
                "O texto formatado usa um recurso que o editor não oferece: " + what + ".");
    }
}
