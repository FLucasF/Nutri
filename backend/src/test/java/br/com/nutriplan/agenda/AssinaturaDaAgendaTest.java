package br.com.nutriplan.agenda;

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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assinatura da agenda em calendário externo (RF76).
 *
 * Não é integração com a API do Google: é um feed iCalendar, o formato que
 * Google Agenda, Apple Calendar e Outlook assinam nativamente. O que fica de
 * fora é o caminho de volta — criar um atendimento aqui a partir de um evento
 * criado lá.
 *
 * O que estes testes protegem é o que quebra em leitor diferente: fuso, UID
 * estável e escape de caractere com significado no formato.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssinaturaDaAgendaTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token;
    private long paciente;

    @BeforeEach
    void preparar() throws Exception {
        token = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Nutri Agenda",
                                "email", "ics" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        paciente = json.readTree(mvc.perform(post("/api/pacientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("nome", "Marina, Duarte e Silva"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String gerarAssinatura() throws Exception {
        return json.readTree(mvc.perform(post("/api/agenda/assinatura")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private long agendar(String hora) throws Exception {
        return json.readTree(mvc.perform(post("/api/agenda")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId":%d,"inicio":"%sT%s:00","duracaoMinutos":60,
                                 "tipo":"PRIMEIRA_CONSULTA","observacao":"Trazer exames"}"""
                                .formatted(paciente, LocalDate.now().plusDays(2), hora)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String baixarCalendario(String assinatura, int esperado) throws Exception {
        return mvc.perform(get("/api/publico/agenda/" + assinatura + ".ics"))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a assinatura só existe depois de pedida")
    void assinaturaSoExisteDepoisDePedida() throws Exception {
        String antes = mvc.perform(get("/api/agenda/assinatura")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var noDoToken = json.readTree(antes).get("token");
        assertThat(noDoToken == null || noDoToken.isNull())
                .as("sem assinatura pedida, o endereco nao existe")
                .isTrue();

        assertThat(gerarAssinatura()).isNotBlank();
    }

    @Test
    @DisplayName("o feed sai no formato iCalendar, com os atendimentos")
    void feedComOsAtendimentos() throws Exception {
        agendar("09:00");
        String assinatura = gerarAssinatura();

        String ics = baixarCalendario(assinatura, 200);

        assertThat(ics).startsWith("BEGIN:VCALENDAR");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("BEGIN:VEVENT").contains("END:VEVENT");
        assertThat(ics).contains("SUMMARY:");
        assertThat(ics).contains("Trazer exames");
        // Quebra de linha do formato é CRLF, e não LF.
        assertThat(ics).contains("\r\n");
    }

    @Test
    @DisplayName("o horário sai em UTC, convertido do fuso de Brasília")
    void horarioEmUtc() throws Exception {
        agendar("09:00");
        String ics = baixarCalendario(gerarAssinatura(), 200);

        // 09:00 em Brasília (UTC−3) é 12:00 UTC. Sem a conversão, o
        // atendimento apareceria três horas fora no calendário do usuário.
        assertThat(ics).contains("T120000Z");
    }

    @Test
    @DisplayName("o UID é estável entre buscas")
    void uidEstavel() throws Exception {
        long id = agendar("09:00");
        String assinatura = gerarAssinatura();

        String primeira = baixarCalendario(assinatura, 200);
        String segunda = baixarCalendario(assinatura, 200);

        // Se o UID mudasse, o calendário apagaria e recriaria o evento a cada
        // atualização, e o alerta tocaria de novo.
        assertThat(primeira).contains("UID:atendimento-" + id + "@nutriplan");
        assertThat(segunda).contains("UID:atendimento-" + id + "@nutriplan");
    }

    @Test
    @DisplayName("vírgula no nome do paciente é escapada")
    void escapaCaractereComSignificado() throws Exception {
        agendar("09:00");
        String ics = baixarCalendario(gerarAssinatura(), 200);

        // "Marina, Duarte e Silva": a vírgula separa valores no formato, e sem
        // escapar o leitor cortaria o nome ali.
        assertThat(ics).contains("Marina\\, Duarte e Silva");
    }

    @Test
    @DisplayName("nenhuma linha passa de 75 octetos")
    void linhasDobradas() throws Exception {
        agendar("09:00");
        String ics = baixarCalendario(gerarAssinatura(), 200);

        for (String linha : ics.split("\r\n")) {
            assertThat(linha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .as("linha: %s", linha)
                    .isLessThanOrEqualTo(75);
        }
    }

    @Test
    @DisplayName("regerar invalida o endereço anterior")
    void regerarInvalidaOAnterior() throws Exception {
        String primeira = gerarAssinatura();
        baixarCalendario(primeira, 200);

        String segunda = gerarAssinatura();
        assertThat(segunda).isNotEqualTo(primeira);

        baixarCalendario(primeira, 404);
        baixarCalendario(segunda, 200);
    }

    @Test
    @DisplayName("desligar a assinatura derruba o feed")
    void desligarDerrubaOFeed() throws Exception {
        String assinatura = gerarAssinatura();
        baixarCalendario(assinatura, 200);

        mvc.perform(delete("/api/agenda/assinatura")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        baixarCalendario(assinatura, 404);
    }

    @Test
    @DisplayName("endereço inventado responde 404")
    void enderecoInventado() throws Exception {
        baixarCalendario("nao-existe", 404);
    }

    @Test
    @DisplayName("o feed traz só a agenda daquele consultório")
    void feedNaoVazaAgendaAlheia() throws Exception {
        agendar("09:00");
        String minhaAssinatura = gerarAssinatura();

        String outro = json.readTree(mvc.perform(post("/api/auth/cadastro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nome", "Bruna",
                                "email", "bruna" + System.nanoTime() + "@exemplo.com",
                                "senha", "senhaSegura1"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        String assinaturaDaOutra = json.readTree(mvc.perform(post("/api/agenda/assinatura")
                        .header("Authorization", "Bearer " + outro))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();

        assertThat(baixarCalendario(assinaturaDaOutra, 200)).doesNotContain("Marina");
        assertThat(baixarCalendario(minhaAssinatura, 200)).contains("Marina");
    }
}
