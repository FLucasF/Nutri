package br.com.nutriplan.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.richtext.RichTextDocument;

/**
 * The editor's schema lives in the browser; this is the copy that guards the
 * database. These tests are the reason the two can be trusted to agree.
 */
@DisplayName("documento de texto rico")
class RichTextDocumentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private void accept(String json) {
        assertThatCode(() -> RichTextDocument.of(json, mapper)).doesNotThrowAnyException();
    }

    private void refuse(String json) {
        assertThatThrownBy(() -> RichTextDocument.of(json, mapper))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("aceita o que o editor sabe produzir")
    void acceptsWhatTheEditorWrites() {
        accept("""
                {"type":"doc","content":[
                  {"type":"paragraph","attrs":{"textAlign":"justify"},
                   "content":[{"type":"text","text":"Objetivo","marks":[{"type":"bold"}]},
                              {"type":"text","text":" do caso","marks":[
                                 {"type":"textStyle","attrs":{"fontSize":12}}]}]},
                  {"type":"bulletList","content":[
                     {"type":"listItem","content":[
                        {"type":"paragraph","content":[{"type":"text","text":"item"}]}]}]},
                  {"type":"table","content":[
                     {"type":"tableRow","content":[
                        {"type":"tableHeader","attrs":{"colspan":2},
                         "content":[{"type":"paragraph"}]},
                        {"type":"tableCell","content":[{"type":"paragraph"}]}]}]}
                ]}""");
    }

    @Test
    @DisplayName("aceita marcador aninhado, que é o Tab dentro da lista")
    void acceptsNestedBullets() {
        // Refusing this would make Tab inside a bullet produce a document the
        // editor can draw and the server will not take — the text would stop
        // saving with nothing on screen to explain it.
        accept("""
                {"type":"doc","content":[
                  {"type":"bulletList","content":[
                     {"type":"listItem","content":[
                        {"type":"paragraph","content":[{"type":"text","text":"café"}]},
                        {"type":"bulletList","content":[
                           {"type":"listItem","content":[
                              {"type":"paragraph","content":[
                                 {"type":"text","text":"mamão"}]}]}]}]}]}]}""");
    }

    @Test
    @DisplayName("campo vazio não é campo inválido")
    void blankIsNotInvalid() {
        assertThat(RichTextDocument.of(null, mapper).json()).isNull();
        assertThat(RichTextDocument.of("   ", mapper).json()).isNull();
    }

    @Test
    @DisplayName("recusa nó que o editor não oferece")
    void refusesNodeOutsideTheEditor() {
        // Heading is switched off in the editor on purpose: the model has no
        // place for it, so letting it through here would store text that the
        // screen could not draw back.
        refuse("""
                {"type":"doc","content":[
                  {"type":"heading","attrs":{"level":1},
                   "content":[{"type":"text","text":"Título"}]}]}""");
        refuse("""
                {"type":"doc","content":[{"type":"codeBlock","content":[]}]}""");
    }

    @Test
    @DisplayName("recusa marca desconhecida")
    void refusesUnknownMark() {
        refuse("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                     {"type":"text","text":"x","marks":[{"type":"link"}]}]}]}""");
    }

    @Test
    @DisplayName("recusa tamanho de fonte fora do conjunto")
    void refusesFontSizeOutsideTheSet() {
        // 13 does not exist in the editor's list. Accepting it here would let a
        // size in that the screen cannot offer and the PDF would render by
        // accident — the very drift this format exists to stop.
        refuse("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                     {"type":"text","text":"x","marks":[
                        {"type":"textStyle","attrs":{"fontSize":13}}]}]}]}""");
        refuse("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                     {"type":"text","text":"x","marks":[
                        {"type":"textStyle","attrs":{"fontSize":"12pt"}}]}]}]}""");
    }

    @Test
    @DisplayName("textStyle sem tamanho é texto no tamanho padrão")
    void textStyleWithoutSizeIsPlainText() {
        accept("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[
                     {"type":"text","text":"x","marks":[
                        {"type":"textStyle","attrs":{"fontSize":null}}]}]}]}""");
    }

    @Test
    @DisplayName("recusa alinhamento inventado")
    void refusesUnknownAlignment() {
        refuse("""
                {"type":"doc","content":[
                  {"type":"paragraph","attrs":{"textAlign":"middle"}}]}""");
    }

    @Test
    @DisplayName("recusa JSON ilegível e documento que não é documento")
    void refusesGarbage() {
        refuse("isto não é json");
        refuse("{\"type\":\"paragraph\"}");
        refuse("[]");
    }

    @Test
    @DisplayName("recusa aninhamento fundo demais")
    void refusesDeepNesting() {
        // A list inside a list inside a list, far past anything a person writes.
        var json = new StringBuilder("{\"type\":\"doc\",\"content\":[");
        int levels = 40;
        for (int i = 0; i < levels; i++) {
            json.append("{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[");
        }
        json.append("{\"type\":\"paragraph\"}");
        for (int i = 0; i < levels; i++) {
            json.append("]}]}");
        }
        json.append("]}");
        refuse(json.toString());
    }

    @Test
    @DisplayName("o texto puro sai na ordem em que foi escrito")
    void plainTextKeepsTheOrder() {
        String plain = RichTextDocument.plainText("""
                {"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"Primeira"}]},
                  {"type":"paragraph","content":[
                     {"type":"text","text":"Seg"},{"type":"text","text":"unda"}]}]}""", mapper);

        assertThat(plain).isEqualTo("Primeira\nSegunda");
    }
}
