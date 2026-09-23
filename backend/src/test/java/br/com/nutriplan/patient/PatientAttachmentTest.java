package br.com.nutriplan.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("arquivos anexos")
class PatientAttachmentTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String outro;
    private long patient;

    @BeforeEach
    void prepare() throws Exception {
        token = register("anexoA");
        outro = register("anexoB");

        String created = mvc.perform(post("/api/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        patient = json.readTree(created).get("id").asLong();
    }

    private String register(String prefix) throws Exception {
        String body = mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Nutri " + prefix,
                                "email", prefix + System.nanoTime() + "@exemplo.com",
                                "password", "passwordSegura1",
                                "crn", "CRN-3 99999"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    // ----------------------------------------------------------------- testes

    @Test
    @DisplayName("o PDF do sistema antigo entra sem precisar ser recriado")
    void theOldPdfComesInWithoutBeingRebuilt() throws Exception {
        // É a resposta à dúvida dele sobre os pacientes ativos: em vez de
        // refazer cardápio um a um, anexa e segue.
        var file = new MockMultipartFile("file", "cardapio-webdiet.pdf",
                "application/pdf", "%PDF-1.4 conteudo".getBytes());

        String created = mvc.perform(multipart("/api/patients/" + patient + "/attachments")
                        .file(file)
                        .param("title", "Cardápio anterior (Webdiet)")
                        .param("referenceDate", "2026-03-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode anexo = json.readTree(created);
        assertThat(anexo.get("kind").asText()).isEqualTo("FILE");
        assertThat(anexo.get("title").asText()).isEqualTo("Cardápio anterior (Webdiet)");
        assertThat(anexo.get("fileSize").asLong()).isPositive();

        byte[] baixado = mvc.perform(
                        get("/api/patients/" + patient + "/attachments/"
                                + anexo.get("id").asLong() + "/file")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(baixado)).startsWith("%PDF");
    }

    @Test
    @DisplayName("o link do plano antigo também fica guardado")
    void theOldPlanLinkIsKeptToo() throws Exception {
        String created = mvc.perform(post("/api/patients/" + patient + "/attachments/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plano no Webdiet",
                                 "url":"https://webdiet.com.br/plano/123"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(created).get("kind").asText()).isEqualTo("LINK");
        assertThat(getJson("/api/patients/" + patient + "/attachments")).hasSize(1);
    }

    @Test
    @DisplayName("o anexo precisa de título, porque o nome do arquivo não diz nada depois")
    void theAttachmentNeedsATitle() throws Exception {
        var file = new MockMultipartFile("file", "documento(1).pdf",
                "application/pdf", "%PDF".getBytes());

        mvc.perform(multipart("/api/patients/" + patient + "/attachments")
                        .file(file)
                        .param("title", "   ")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(422));
    }

    @Test
    @DisplayName("link sem esquema é recusado")
    void aLinkWithoutASchemeIsRefused() throws Exception {
        mvc.perform(post("/api/patients/" + patient + "/attachments/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem esquema\",\"url\":\"webdiet.com.br/123\"}"))
                .andExpect(status().is(422));
    }

    @Test
    @DisplayName("o anexo de um consultório não existe para o outro")
    void attachmentsDoNotCrossAccounts() throws Exception {
        String created = mvc.perform(post("/api/patients/" + patient + "/attachments/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Plano\",\"url\":\"https://exemplo.com/1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        mvc.perform(delete("/api/patients/" + patient + "/attachments/" + id)
                        .header("Authorization", "Bearer " + outro))
                .andExpect(status().isNotFound());
    }
}
