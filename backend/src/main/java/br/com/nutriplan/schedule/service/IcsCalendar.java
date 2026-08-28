package br.com.nutriplan.schedule.service;

import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.dto.ScheduleDtos;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The schedule in iCalendar format (RFC 5545).
 *
 * It is the format Google Calendar, Apple Calendar and Outlook subscribe to
 * natively: the calendar fetches the address from time to time and reflects
 * what changed. It gives the nutritionist what they want — seeing the
 * appointments in the calendar they already use — without an application
 * credential, a consent screen or a second token system to maintain.
 *
 * Two requirements of the format tend to go unnoticed and break in different
 * readers:
 *
 *  - the line cannot exceed 75 octets, and the continuation is a break
 *    followed by a space;
 *  - the UID has to be stable between fetches. If it changed, the calendar
 *    would delete and recreate the event on every update, and the alert would
 *    ring again.
 */
@Component
public class IcsCalendar {

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'");
    private static final ZoneId UTC_ZONE = ZoneId.of("UTC");
    private static final ZoneId TIMEZONE_LOCAL = ZoneId.of("America/Sao_Paulo");

    /** Octet limit per line, as required by the RFC. */
    private static final int ROW_LIMIT = 75;

    public String generate(String practiceName,
                        List<ScheduleDtos.AppointmentResponse> appointments) {
        var output = new StringBuilder();
        write(output, "BEGIN:VCALENDAR");
        write(output, "VERSION:2.0");
        write(output, "PRODID:-//NutriPlan//Agenda//PT-BR");
        write(output, "CALSCALE:GREGORIAN");
        write(output, "METHOD:PUBLISH");
        write(output, "X-WR-CALNAME:" + escapar(
                practiceName == null ? "Agenda" : practiceName));
        write(output, "X-WR-TIMEZONE:" + TIMEZONE_LOCAL.getId());

        for (ScheduleDtos.AppointmentResponse a : appointments) {
            write(output, "BEGIN:VEVENT");
            // Stable between fetches: without this the calendar would delete and
            // recreate the event on every update, and the alert would ring
            // again.
            write(output, "UID:appointment-" + a.id() + "@nutriplan");
            write(output, "DTSTAMP:" + inUtc(a.start()));
            write(output, "DTSTART:" + inUtc(a.start()));
            write(output, "DTEND:" + inUtc(a.end()));
            write(output, "SUMMARY:" + escapar(summary(a)));
            if (a.notes() != null && !a.notes().isBlank()) {
                write(output, "DESCRIPTION:" + escapar(a.notes()));
            }
            write(output, "STATUS:" + statusIcs(a.status()));
            write(output, "END:VEVENT");
        }

        write(output, "END:VCALENDAR");
        return output.toString();
    }

    private String summary(ScheduleDtos.AppointmentResponse a) {
        String name = a.patientName() == null ? "Atendimento" : a.patientName();
        return a.typeDescription() == null ? name : name + " — " + a.typeDescription();
    }

    /**
     * Canceled becomes TENTATIVE and not CANCELLED on purpose.
     *
     * CANCELLED disappears from the view in a good share of readers, and an
     * hour that has just freed up is exactly what the professional wants to see
     * on the schedule.
     */
    private String statusIcs(AppointmentStatus status) {
        return switch (status) {
            case CONFIRMED, COMPLETED -> "CONFIRMED";
            case CANCELED, NOSHOW -> "TENTATIVE";
            default -> "TENTATIVE";
        };
    }

    private String inUtc(java.time.LocalDateTime local) {
        return ZonedDateTime.of(local, TIMEZONE_LOCAL).withZoneSameInstant(UTC_ZONE).format(UTC);
    }

    /** Comma, semicolon, slash and line break have meaning in the format. */
    private String escapar(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /** Writes the line already folded at 75 octets, with the continuation by space. */
    private void write(StringBuilder output, String row) {
        byte[] bytes = row.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= ROW_LIMIT) {
            output.append(row).append("\r\n");
            return;
        }
        int start = 0;
        boolean first = true;
        while (start < bytes.length) {
            int limit = first ? ROW_LIMIT : ROW_LIMIT - 1;
            int end = Math.min(start + limit, bytes.length);
            // It does not cut in the middle of a multibyte character: a UTF-8
            // continuation starts with the bits 10, and cutting there would
            // produce garbage in the reader.
            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            output.append(first ? "" : " ")
                    .append(new String(bytes, start, end - start,
                            java.nio.charset.StandardCharsets.UTF_8))
                    .append("\r\n");
            start = end;
            first = false;
        }
    }
}
