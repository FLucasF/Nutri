package br.com.nutriplan.shared;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O que a API responde quando o pedido esta errado.
 *
 * Estes tres casos caiam todos no manipulador generico e viravam 500 "Ocorreu
 * um erro inesperado". Erro interno e uma afirmacao sobre o servidor; pedido
 * malformado e uma afirmacao sobre o pedido, e so a segunda ajuda quem chamou a
 * consertar o que fez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrosDaApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void preparar() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Erros",
                                "email", "erros" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("rota que não existe responde 404, e não 500")
    void rotaInexistenteResponde404() throws Exception {
        String corpo = mvc.perform(get("/api/nao-existe-esta-rota")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corpo).get("mensagem").asText())
                .contains("não existe");
    }

    @Test
    @DisplayName("método HTTP errado responde 405 dizendo o que a rota aceita")
    void metodoErradoResponde405() throws Exception {
        // GET numa rota que so aceita POST e DELETE. Antes respondia 500, e o
        // log recebia uma pilha inteira por um engano de quem chamou.
        String corpo = mvc.perform(get("/api/alimentos/1/medidas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andReturn().getResponse().getContentAsString();

        // 405 e nao 404: o caminho existe, e dizer "nao encontrado" mandaria
        // quem chamou conferir justamente a parte que esta certa.
        assertThat(json.readTree(corpo).get("mensagem").asText())
                .contains("POST");
    }

    @Test
    @DisplayName("valor fora do enum diz qual é o campo e o que ele aceita")
    void enumInvalidoDizOCampoEOsValores() throws Exception {
        String corpo = mvc.perform(post("/api/prescricoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Plano","metodo":"QUANTITATIVO","refeicoes":[]}"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        var no = json.readTree(corpo);
        // A mensagem sozinha resolve: nomeia o campo e lista o que serve.
        assertThat(no.get("mensagem").asText())
                .contains("metodo")
                .contains("ALIMENTOS")
                .contains("QUALITATIVO")
                .contains("EQUIVALENTES");
        // E vem tambem por campo, para a tela grudar no campo certo.
        assertThat(no.get("campos").get(0).get("campo").asText()).isEqualTo("metodo");
    }

    @Test
    @DisplayName("o caminho do campo inválido inclui a posição na lista")
    void caminhoDoCampoIncluiOIndice() throws Exception {
        String corpo = mvc.perform(post("/api/prescricoes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo":"Plano","metodo":"ALIMENTOS","refeicoes":[
                                  {"nome":"Almoço","itens":[{"quantidade":"muito"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        // Sem o indice, "quantidade" nao diz qual item de qual refeicao.
        assertThat(json.readTree(corpo).get("campos").get(0).get("campo").asText())
                .isEqualTo("refeicoes[0].itens[0].quantidade");
    }

    @Test
    @DisplayName("identificador que não é número responde 400 nomeando o parâmetro")
    void parametroDeTipoErrado() throws Exception {
        String corpo = mvc.perform(get("/api/pacientes/abc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corpo).get("mensagem").asText()).contains("id");
    }

    @Test
    @DisplayName("JSON quebrado responde 400 dizendo que o conteúdo não pôde ser lido")
    void jsonQuebrado() throws Exception {
        String corpo = mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": "))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(corpo).get("mensagem").asText()).contains("não pôde ser lido");
    }
}
