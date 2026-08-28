package br.com.nutriplan.agenda.service;

import br.com.nutriplan.agenda.domain.SituacaoAtendimento;
import br.com.nutriplan.agenda.dto.AgendaDtos;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A agenda no formato iCalendar (RFC 5545).
 *
 * É o formato que Google Agenda, Apple Calendar e Outlook assinam nativamente:
 * o calendário busca o endereço de tempos em tempos e reflete o que mudou. Dá
 * ao nutricionista o que ele quer — ver os atendimentos no calendário que já
 * usa — sem credencial de aplicativo, tela de consentimento nem um segundo
 * sistema de tokens para manter.
 *
 * Duas exigências do formato costumam passar despercebidas e quebram em
 * leitores diferentes:
 *
 *  - a linha não pode passar de 75 octetos, e a continuação é uma quebra
 *    seguida de espaço;
 *  - o UID precisa ser estável entre buscas. Se mudasse, o calendário apagaria
 *    e recriaria o evento a cada atualização, e o alerta tocaria de novo.
 */
@Component
public class CalendarioIcs {

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final ZoneId FUSO_LOCAL = ZoneId.of("America/Sao_Paulo");

    /** Limite de octetos por linha, conforme a RFC. */
    private static final int LIMITE_DA_LINHA = 75;

    public String gerar(String nomeDoConsultorio,
                        List<AgendaDtos.AgendamentoResponse> atendimentos) {
        var saida = new StringBuilder();
        escrever(saida, "BEGIN:VCALENDAR");
        escrever(saida, "VERSION:2.0");
        escrever(saida, "PRODID:-//NutriPlan//Agenda//PT-BR");
        escrever(saida, "CALSCALE:GREGORIAN");
        escrever(saida, "METHOD:PUBLISH");
        escrever(saida, "X-WR-CALNAME:" + escapar(
                nomeDoConsultorio == null ? "Agenda" : nomeDoConsultorio));
        escrever(saida, "X-WR-TIMEZONE:" + FUSO_LOCAL.getId());

        for (AgendaDtos.AgendamentoResponse a : atendimentos) {
            escrever(saida, "BEGIN:VEVENT");
            // Estável entre buscas: sem isso o calendário apagaria e recriaria
            // o evento a cada atualização, e o alerta tocaria de novo.
            escrever(saida, "UID:atendimento-" + a.id() + "@nutriplan");
            escrever(saida, "DTSTAMP:" + emUtc(a.inicio()));
            escrever(saida, "DTSTART:" + emUtc(a.inicio()));
            escrever(saida, "DTEND:" + emUtc(a.fim()));
            escrever(saida, "SUMMARY:" + escapar(resumo(a)));
            if (a.observacao() != null && !a.observacao().isBlank()) {
                escrever(saida, "DESCRIPTION:" + escapar(a.observacao()));
            }
            escrever(saida, "STATUS:" + statusIcs(a.situacao()));
            escrever(saida, "END:VEVENT");
        }

        escrever(saida, "END:VCALENDAR");
        return saida.toString();
    }

    private String resumo(AgendaDtos.AgendamentoResponse a) {
        String nome = a.pacienteNome() == null ? "Atendimento" : a.pacienteNome();
        return a.tipoDescricao() == null ? nome : nome + " — " + a.tipoDescricao();
    }

    /**
     * Cancelado vira TENTATIVE e não CANCELLED de propósito.
     *
     * CANCELLED some da visualização em boa parte dos leitores, e um horário
     * que vagou é justamente o que o profissional quer enxergar na agenda.
     */
    private String statusIcs(SituacaoAtendimento situacao) {
        return switch (situacao) {
            case CONFIRMADO, REALIZADO -> "CONFIRMED";
            case CANCELADO, FALTOU -> "TENTATIVE";
            default -> "TENTATIVE";
        };
    }

    private String emUtc(java.time.LocalDateTime local) {
        return ZonedDateTime.of(local, FUSO_LOCAL).withZoneSameInstant(UTC_ZONE).format(UTC);
    }

    /** Vírgula, ponto e vírgula, barra e quebra de linha têm significado no formato. */
    private String escapar(String texto) {
        return texto.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /** Escreve a linha já dobrada em 75 octetos, com a continuação por espaço. */
    private void escrever(StringBuilder saida, String linha) {
        byte[] bytes = linha.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= LIMITE_DA_LINHA) {
            saida.append(linha).append("\r\n");
            return;
        }
        int inicio = 0;
        boolean primeira = true;
        while (inicio < bytes.length) {
            int limite = primeira ? LIMITE_DA_LINHA : LIMITE_DA_LINHA - 1;
            int fim = Math.min(inicio + limite, bytes.length);
            // Não corta no meio de um caractere multibyte: continuação de UTF-8
            // começa com os bits 10, e cortar ali produziria lixo no leitor.
            while (fim < bytes.length && (bytes[fim] & 0xC0) == 0x80) {
                fim--;
            }
            saida.append(primeira ? "" : " ")
                    .append(new String(bytes, inicio, fim - inicio,
                            java.nio.charset.StandardCharsets.UTF_8))
                    .append("\r\n");
            inicio = fim;
            primeira = false;
        }
    }
}
