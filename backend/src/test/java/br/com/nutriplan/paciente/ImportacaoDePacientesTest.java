package br.com.nutriplan.paciente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Importação de pacientes a partir de planilha (RF14).
 *
 * O que estes testes protegem é o comportamento diante de planilha real: ela
 * sempre tem uma linha torta, uma data em formato diferente e um CPF repetido.
 * Abortar o arquivo inteiro por causa disso obrigaria o usuário a caçar o
 * defeito sem pista nenhuma.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportacaoDePacientesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;

    @BeforeEach
    void autenticar() throws Exception {
        String corpo = mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Importacao",
                                "email", "import" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(corpo).get("token").asText();
    }

    private JsonNode importar(String csv) throws Exception {
        var arquivo = new MockMultipartFile(
                "arquivo", "pacientes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String corpo = mvc.perform(multipart("/api/pacientes/importar").file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private JsonNode listar() throws Exception {
        String corpo = mvc.perform(get("/api/pacientes").param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("content");
    }

    @Test
    @DisplayName("importa a planilha e preenche os campos reconhecidos")
    void importaPlanilha() throws Exception {
        JsonNode r = importar("""
                nome,email,telefone,nascimento,sexo,objetivo
                Marina Duarte,marina@exemplo.com,11999990000,12/03/1992,F,Reeducação alimentar
                Carlos Menezes,carlos@exemplo.com,11988887777,1986-07-05,Masculino,Ganho de massa
                """);

        assertThat(r.get("importados").asInt()).isEqualTo(2);
        assertThat(r.get("ignorados").asInt()).isZero();

        JsonNode lista = listar();
        assertThat(lista).hasSize(2);
        JsonNode marina = lista.get(1).get("nome").asText().startsWith("Marina")
                ? lista.get(1) : lista.get(0);
        assertThat(marina.get("email").asText()).isEqualTo("marina@exemplo.com");
        // Nascida em 1992: a idade é derivada, e prova que a data entrou.
        assertThat(marina.get("idade").isNull()).isFalse();
    }

    @Test
    @DisplayName("aceita os formatos de data usados em planilha brasileira")
    void aceitaFormatosDeData() throws Exception {
        JsonNode r = importar("""
                nome,nascimento
                Um,12/03/1992
                Dois,1992-03-12
                Tres,5/7/1986
                Quatro,12-03-1992
                """);

        assertThat(r.get("importados").asInt()).isEqualTo(4);
        for (JsonNode p : listar()) {
            assertThat(p.get("idade").isNull())
                    .as("idade de %s", p.get("nome").asText())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("linha sem nome é ignorada, e as outras entram")
    void linhaSemNomeNaoDerrubaOArquivo() throws Exception {
        JsonNode r = importar("""
                nome,email
                Marina Duarte,marina@exemplo.com
                ,orfao@exemplo.com
                Carlos Menezes,carlos@exemplo.com
                """);

        assertThat(r.get("importados").asInt()).isEqualTo(2);
        assertThat(r.get("ignorados").asInt()).isEqualTo(1);
        assertThat(r.get("avisos").toString()).contains("Linha 3");
    }

    @Test
    @DisplayName("dado ilegivel nao descarta o paciente")
    void dadoIlegivelNaoDescartaOPaciente() throws Exception {
        JsonNode r = importar("""
                nome,email,nascimento,sexo
                Marina Duarte,nao tenho,ontem,indefinido
                """);

        // O nome é o que torna o cadastro útil; o resto se corrige na ficha.
        assertThat(r.get("importados").asInt()).isEqualTo(1);
        JsonNode p = listar().get(0);
        assertThat(p.get("nome").asText()).isEqualTo("Marina Duarte");
        // Campo sem valor sai omitido da resposta, e nao como nulo ou vazio.
        assertThat(ausente(p, "email")).isTrue();
        assertThat(ausente(p, "idade")).isTrue();
        assertThat(r.get("avisos").toString()).contains("data de nascimento");
    }

    private boolean ausente(JsonNode no, String campo) {
        return !no.has(campo) || no.get(campo).isNull();
    }

    @Test
    @DisplayName("nao importa paciente com CPF ja cadastrado")
    void naoDuplicaPorCpf() throws Exception {
        mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Marina Duarte", "cpf", "12345678901"))))
                .andExpect(status().isCreated());

        JsonNode r = importar("""
                nome,cpf
                Marina Duarte,123.456.789-01
                Carlos Menezes,98765432100
                """);

        // O CPF da planilha vem pontuado; a comparação é por dígito.
        assertThat(r.get("importados").asInt()).isEqualTo(1);
        assertThat(r.get("ignorados").asInt()).isEqualTo(1);
        assertThat(r.get("avisos").toString()).contains("CPF");
        assertThat(listar()).hasSize(2);
    }

    @Test
    @DisplayName("o limite do plano vale na importacao, e ela diz onde parou")
    void respeitaOLimiteDoPlano() throws Exception {
        // O plano experimental permite 5 pacientes ativos.
        JsonNode r = importar("""
                nome
                Um
                Dois
                Tres
                Quatro
                Cinco
                Seis
                Sete
                """);

        assertThat(r.get("importados").asInt()).isEqualTo(5);
        assertThat(r.get("avisos").toString()).contains("Importação interrompida");
        assertThat(listar()).hasSize(5);
    }

    @Test
    @DisplayName("recusa arquivo vazio")
    void recusaArquivoVazio() throws Exception {
        var vazio = new MockMultipartFile("arquivo", "vazio.csv", "text/csv", new byte[0]);
        mvc.perform(multipart("/api/pacientes/importar").file(vazio)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("avisa quando a coluna de nome nao foi encontrada")
    void avisaColunaDeNomeAusente() throws Exception {
        JsonNode r = importar("""
                email,telefone
                marina@exemplo.com,11999990000
                """);

        assertThat(r.get("importados").asInt()).isZero();
        assertThat(r.get("avisos").toString()).contains("nome do paciente");
    }
}
