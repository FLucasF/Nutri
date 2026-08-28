package br.com.nutriplan.schedule.web;

import br.com.nutriplan.schedule.service.ScheduleSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * The schedule's iCalendar feed.
 *
 * Without authentication: a calendar that subscribes to an address does not
 * know how to send a token header. The authorization is possession of the
 * address, a UUID — the same as the public plan, with the same caveat that
 * whoever receives it, sees.
 */
@RestController
@RequestMapping("/api/public/schedule")
@RequiredArgsConstructor
@Tag(name = "Assinatura da agenda")
public class PublicCalendarController {

    private final ScheduleSubscriptionService subscriptionService;

    @GetMapping(value = "/{token}.ics", produces = "text/calendar;charset=UTF-8")
    @Operation(summary = "Calendário da agenda, para assinar no Google, Apple ou Outlook",
            description = "Um mês para trás e seis para frente. O calendário busca este "
                    + "endereço de tempos em tempos e reflete o que mudou.")
    public ResponseEntity<byte[]> calendar(@PathVariable String token) {
        String ics = subscriptionService.calendarDe(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"agenda.ics\"")
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }
}
