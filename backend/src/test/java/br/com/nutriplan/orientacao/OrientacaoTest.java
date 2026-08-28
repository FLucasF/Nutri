package br.com.nutriplan.orientacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Orientações nutricionais (RF110–RF113).
 *
 * A regra que estes testes protegem é a cópia: o texto entregue ao paciente não
 * pode mudar porque alguém corrigiu o modelo depois. É a mesma regra do peso
 * gravado no item de refeição, aplicada a texto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrientacaoTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long plano;

    @BeforeEach
    void preparar() throws Exception {
        token = cadastrar("orient");
        tokenB = cadastrar("orientB");
        plano = criarPlano();
    }

    private String cadastrar(String prefixo) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    private long criarPlano() throws Exception {
        long paciente = json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Marina Duarte"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String corpo = """
                {"titulo":"Plano","pacienteId":%d,"metodo":"QUALITATIVO","modelo":false,
                 "refeicoes":[{"nome":"Almoco","itens":[{"descricao":"Salada a vontade"}]}]}"""
                .formatted(paciente);
        return json.readTree(mvc.perform(post("/api/prescricoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode getJson(String tk, String url) throws Exception {
        return json.readTree(mvc.perform(get(url).header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postJson(String tk, String url, String corpo, int esperado) throws Exception {
        String r = mvc.perform(post(url).header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
        return r.isEmpty() ? null : json.readTree(r);
    }

    private long criarPropria(String tk, String titulo, String corpo) throws Exception {
        return postJson(tk, "/api/orientacoes",
                json.writeValueAsString(Map.of("titulo", titulo, "corpo", corpo)), 201)
                .get("id").asLong();
    }

    // ------------------------------------------------------------- biblioteca

    @Test
    @DisplayName("o sistema traz modelos, e eles não são editáveis")
    void modelosDoSistemaNaoSaoEditaveis() throws Exception {
        JsonNode lista = getJson(token, "/api/orientacoes").get("content");
        assertThat(lista).isNotEmpty();

        JsonNode modelo = lista.get(0);
        assertThat(modelo.get("modeloDoSistema").asBoolean()).isTrue();
        assertThat(modelo.get("editavel").asBoolean()).isFalse();

        mvc.perform(put("/api/orientacoes/" + modelo.get("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Meu jeito","corpo":"Texto alterado"}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("duplicar um modelo cria a minha versão, sem tocar no original")
    void duplicarCriaVersaoPropria() throws Exception {
        JsonNode modelo = getJson(token, "/api/orientacoes").get("content").get(0);
        long modeloId = modelo.get("id").asLong();
        String tituloOriginal = modelo.get("titulo").asText();

        JsonNode copia = postJson(token, "/api/orientacoes/" + modeloId + "/duplicar", "", 201);

        assertThat(copia.get("editavel").asBoolean()).isTrue();
        assertThat(copia.get("corpo").asText()).isEqualTo(modelo.get("corpo").asText());
        assertThat(getJson(token, "/api/orientacoes/" + modeloId).get("titulo").asText())
                .isEqualTo(tituloOriginal);
    }

    @Test
    @DisplayName("a orientação própria não aparece para outro consultório")
    void orientacaoPropriaEhDoConsultorio() throws Exception {
        criarPropria(token, "Minha conduta", "Texto interno");

        String lista = getJson(tokenB, "/api/orientacoes").toString();
        assertThat(lista).doesNotContain("Minha conduta");
    }

    @Test
    @DisplayName("a minha orientação vem antes dos modelos do sistema")
    void propriaVemPrimeiro() throws Exception {
        criarPropria(token, "AAA minha", "Texto");

        JsonNode primeira = getJson(token, "/api/orientacoes").get("content").get(0);
        assertThat(primeira.get("modeloDoSistema").asBoolean()).isFalse();
    }

    // ---------------------------------------------------------------- no plano

    @Test
    @DisplayName("anexar copia o texto da biblioteca para o plano")
    void anexarCopiaOTexto() throws Exception {
        long id = criarPropria(token, "Como montar o prato", "Metade de vegetais.");

        JsonNode anexo = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201);

        assertThat(anexo.get("titulo").asText()).isEqualTo("Como montar o prato");
        assertThat(anexo.get("corpo").asText()).isEqualTo("Metade de vegetais.");
        assertThat(anexo.get("orientacaoId").asLong()).isEqualTo(id);
    }

    @Test
    @DisplayName("editar a biblioteca não altera o que já foi anexado")
    void edicaoNaBibliotecaNaoAlcancaOPlano() throws Exception {
        long id = criarPropria(token, "Hidratacao", "Beba 2 litros.");
        postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201);

        mvc.perform(put("/api/orientacoes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Hidratacao","corpo":"Beba 3 litros."}"""))
                .andExpect(status().isOk());

        JsonNode anexado = getJson(token, "/api/prescricoes/" + plano + "/orientacoes").get(0);
        assertThat(anexado.get("corpo").asText())
                .as("o paciente recebeu 2 litros; corrigir o modelo depois nao reescreve isso")
                .isEqualTo("Beba 2 litros.");
    }

    @Test
    @DisplayName("o texto anexado pode ser adaptado ao paciente sem sujar o modelo")
    void textoAnexadoEhEditavel() throws Exception {
        long id = criarPropria(token, "Hidratacao", "Beba 2 litros.");
        long anexoId = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201).get("id").asLong();

        mvc.perform(put("/api/prescricoes/" + plano + "/orientacoes/" + anexoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Hidratacao","corpo":"Beba 2,5 litros — voce treina."}"""))
                .andExpect(status().isOk());

        assertThat(getJson(token, "/api/orientacoes/" + id).get("corpo").asText())
                .isEqualTo("Beba 2 litros.");
    }

    @Test
    @DisplayName("dá para anexar um texto escrito na hora, sem biblioteca")
    void anexaTextoAvulso() throws Exception {
        JsonNode anexo = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"titulo":"Recado","corpo":"Trazer o exame na proxima consulta."}""", 201);

        assertThat(anexo.get("titulo").asText()).isEqualTo("Recado");
        // Campo sem valor sai omitido da resposta, e nao como nulo.
        assertThat(anexo.has("orientacaoId") && !anexo.get("orientacaoId").isNull())
                .as("texto avulso nao tem origem na biblioteca")
                .isFalse();
    }

    @Test
    @DisplayName("anexo sem texto e sem origem é recusado")
    void recusaAnexoVazio() throws Exception {
        postJson(token, "/api/prescricoes/" + plano + "/orientacoes", "{}", 422);
    }

    @Test
    @DisplayName("as orientações chegam ao paciente pelo link do plano")
    void orientacaoChegaAoPaciente() throws Exception {
        postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"titulo":"Como montar o prato","corpo":"Metade de vegetais."}""", 201);
        mvc.perform(post("/api/prescricoes/" + plano + "/publicar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String link = getJson(token, "/api/prescricoes/" + plano)
                .get("identificadorPublico").asText();
        String publico = mvc.perform(get("/api/publico/planos/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(publico).contains("Como montar o prato");
        assertThat(publico).contains("Metade de vegetais.");
    }

    @Test
    @DisplayName("um consultório não anexa orientação no plano de outro")
    void naoAnexaEmPlanoAlheio() throws Exception {
        postJson(tokenB, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"titulo":"Invasao","corpo":"Texto"}""", 404);
    }

    @Test
    @DisplayName("desanexar tira do plano e mantém a biblioteca")
    void desanexarMantemABiblioteca() throws Exception {
        long id = criarPropria(token, "Hidratacao", "Beba 2 litros.");
        long anexoId = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201).get("id").asLong();

        mvc.perform(delete("/api/prescricoes/" + plano + "/orientacoes/" + anexoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/prescricoes/" + plano + "/orientacoes")).isEmpty();
        assertThat(getJson(token, "/api/orientacoes/" + id).get("titulo").asText())
                .isEqualTo("Hidratacao");
    }

    // ----------------------------------------------------------------- imagem

    @Test
    @DisplayName("a orientação aceita uma imagem, e ela vai junto para o plano")
    void imagemAcompanhaOAnexo() throws Exception {
        long id = criarPropria(token, "Como montar o prato", "Metade de vegetais.");

        var figura = new org.springframework.mock.web.MockMultipartFile(
                "arquivo", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/orientacoes/" + id + "/imagem").file(figura)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(getJson(token, "/api/orientacoes/" + id).get("temImagem").asBoolean()).isTrue();

        long anexoId = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201).get("id").asLong();

        // A cópia foi junto: o plano tem a própria imagem.
        var resposta = mvc.perform(get("/api/prescricoes/" + plano + "/orientacoes/"
                        + anexoId + "/imagem").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(resposta.getContentType()).startsWith("image/png");
        assertThat(resposta.getContentAsByteArray()).hasSize(6);
    }

    @Test
    @DisplayName("a imagem entregue chega ao paciente pelo link do plano")
    void imagemChegaAoPacientePeloLink() throws Exception {
        long id = criarPropria(token, "Como montar o prato", "Metade de vegetais.");
        var figura = new org.springframework.mock.web.MockMultipartFile(
                "arquivo", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/orientacoes/" + id + "/imagem").file(figura)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201);
        mvc.perform(post("/api/prescricoes/" + plano + "/publicar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String link = getJson(token, "/api/prescricoes/" + plano)
                .get("identificadorPublico").asText();
        var publico = json.readTree(mvc.perform(get("/api/publico/planos/" + link))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        // O endereco vem montado da resposta: a pagina do paciente nao precisa
        // conhecer o formato da rota.
        String endereco = publico.get("orientacoesAnexadas").get(0).get("imagem").asText();
        assertThat(endereco).contains(link);

        var arquivo = mvc.perform(get(endereco))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(arquivo.getContentType()).startsWith("image/png");
        assertThat(arquivo.getContentAsByteArray()).hasSize(6);
    }

    @Test
    @DisplayName("o link de um plano não abre a imagem de outro")
    void linkNaoAbreImagemDeOutroPlano() throws Exception {
        long id = criarPropria(token, "Como montar o prato", "Metade de vegetais.");
        var figura = new org.springframework.mock.web.MockMultipartFile(
                "arquivo", "prato.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/orientacoes/" + id + "/imagem").file(figura)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        long anexoId = postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201).get("id").asLong();
        mvc.perform(post("/api/prescricoes/" + plano + "/publicar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long outro = criarPlano();
        mvc.perform(post("/api/prescricoes/" + outro + "/publicar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String linkDoOutro = getJson(token, "/api/prescricoes/" + outro)
                .get("identificadorPublico").asText();

        // Responde como inexistente, e nao como proibido: quem tem este link
        // nao precisa saber que o outro plano existe.
        mvc.perform(get("/api/publico/planos/" + linkDoOutro
                        + "/orientacoes/" + anexoId + "/imagem"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("arquivo que não é imagem é recusado")
    void recusaArquivoQueNaoEhImagem() throws Exception {
        long id = criarPropria(token, "Texto", "Corpo");
        var arquivo = new org.springframework.mock.web.MockMultipartFile(
                "arquivo", "planilha.csv", "text/csv", "a,b".getBytes());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/orientacoes/" + id + "/imagem").file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("orientação sem imagem responde 404 no download")
    void semImagemResponde404() throws Exception {
        long id = criarPropria(token, "Texto", "Corpo");
        mvc.perform(get("/api/orientacoes/" + id + "/imagem")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("remover a orientação da biblioteca não apaga o que foi entregue")
    void removerNaoApagaOEntregue() throws Exception {
        long id = criarPropria(token, "Hidratacao", "Beba 2 litros.");
        postJson(token, "/api/prescricoes/" + plano + "/orientacoes",
                """
                {"orientacaoId":%d}""".formatted(id), 201);

        mvc.perform(delete("/api/orientacoes/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        JsonNode anexado = getJson(token, "/api/prescricoes/" + plano + "/orientacoes").get(0);
        assertThat(anexado.get("corpo").asText()).isEqualTo("Beba 2 litros.");
    }
}
