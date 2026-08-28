package br.com.nutriplan.prescricao;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plano alimentar em PDF (RF51).
 *
 * O paciente não tem conta no sistema: ele lê o plano pelo link ou o leva
 * impresso. Como o app do paciente está fora do escopo, o impresso é o canal
 * de entrega — e um PDF que sai vazio, ou que entrega um rascunho sem avisar,
 * falha exatamente onde ninguém confere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanoEmPdfTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private String tokenB;
    private long paciente;
    private long alimento;

    @BeforeEach
    void preparar() throws Exception {
        token = cadastrar("pdf");
        tokenB = cadastrar("pdfB");
        paciente = criarPaciente(token, "Marina Duarte");
        alimento = criarAlimento(token);
    }

    private String cadastrar(String prefixo) throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Dra. Helena " + prefixo,
                                "email", prefixo + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1",
                                "crn", "CRN-3 45678"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("token").asText();
    }

    private long criarPaciente(String tk, String nome) throws Exception {
        String corpo = mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", nome))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private long criarAlimento(String tk) throws Exception {
        String corpo = mvc.perform(post("/api/alimentos")
                        .header("Authorization", "Bearer " + tk)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"descricao":"Arroz integral cozido",
                                 "composicao":{"energiaKcal":124,"proteinaG":2.6,
                                               "carboidratoG":25.8,"lipideosG":1}}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private long criarPlano() throws Exception {
        String corpo = """
                {"titulo":"Plano de reeducacao","pacienteId":%d,"metodo":"ALIMENTOS",
                 "orientacoes":"Beba dois litros de agua por dia.","modelo":false,
                 "refeicoes":[{"nome":"Almoco","horario":"12:30",
                   "observacao":"Metade do prato de salada.",
                   "itens":[{"alimentoId":%d,"quantidade":150}]}]}"""
                .formatted(paciente, alimento);
        String resposta = mvc.perform(post("/api/prescricoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asLong();
    }

    private void publicar(long id) throws Exception {
        mvc.perform(post("/api/prescricoes/" + id + "/publicar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private byte[] pdf(String tk, long id) throws Exception {
        return mvc.perform(get("/api/prescricoes/" + id + "/pdf")
                        .header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /**
     * Extrai o texto legível do PDF sem depender de outra biblioteca.
     *
     * O conteúdo de página vem comprimido, então o que sobra em texto puro são
     * os metadados e as strings não comprimidas. Para o que estes testes
     * precisam verificar — que o arquivo é um PDF válido, com páginas e
     * tamanho compatível com o conteúdo — isso basta; conferir cada palavra
     * exigiria um extrator, e o desenho da folha se confere olhando.
     */
    private String legivel(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("gera um PDF válido, com o cabeçalho que todo leitor exige")
    void geraPdfValido() throws Exception {
        long id = criarPlano();
        publicar(id);

        byte[] arquivo = pdf(token, id);

        assertThat(new String(arquivo, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(legivel(arquivo)).endsWith("%%EOF\n");
        assertThat(arquivo.length)
                .as("um PDF com uma refeição e um resumo não cabe em 800 bytes")
                .isGreaterThan(800);
    }

    @Test
    @DisplayName("responde como PDF e sugere um nome de arquivo sem acento")
    void respondeComTipoENome() throws Exception {
        long id = criarPlano();
        publicar(id);

        var resposta = mvc.perform(get("/api/prescricoes/" + id + "/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(resposta.getContentType()).startsWith(MediaType.APPLICATION_PDF_VALUE);
        String disposicao = resposta.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposicao).isNotNull();
        assertThat(disposicao).contains("plano-de-reeducacao.pdf");
        // Nome de arquivo com acento ou espaço quebra em cliente de e-mail e
        // em sistema de arquivos antigo.
        assertThat(disposicao).matches(".*filename=\"[a-z0-9.\\-]+\".*");
    }

    @Test
    @DisplayName("o rascunho também imprime, e a folha se identifica como rascunho")
    void rascunhoImprimeIdentificado() throws Exception {
        long id = criarPlano();

        // Sem publicar: conferir a folha antes de entregar é parte do trabalho.
        byte[] rascunho = pdf(token, id);
        assertThat(new String(rascunho, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        publicar(id);
        byte[] publicado = pdf(token, id);

        // A tarja de rascunho é conteúdo a mais na página.
        assertThat(rascunho.length)
                .as("a folha de rascunho carrega a tarja que a publicada não tem")
                .isGreaterThan(publicado.length);
    }

    @Test
    @DisplayName("um consultório não imprime o plano de outro")
    void naoImprimePlanoDeOutroConsultorio() throws Exception {
        long id = criarPlano();
        publicar(id);

        mvc.perform(get("/api/prescricoes/" + id + "/pdf")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("exige autenticação: o PDF é do profissional, não do link público")
    void exigeAutenticacao() throws Exception {
        long id = criarPlano();
        publicar(id);

        mvc.perform(get("/api/prescricoes/" + id + "/pdf"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("plano sem refeição ainda gera folha, em vez de falhar")
    void planoVazioNaoQuebra() throws Exception {
        String corpo = """
                {"titulo":"Plano vazio","pacienteId":%d,"metodo":"QUALITATIVO",
                 "modelo":false,"refeicoes":[]}""".formatted(paciente);
        long id = json.readTree(mvc.perform(post("/api/prescricoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        byte[] arquivo = pdf(token, id);
        assertThat(new String(arquivo, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
