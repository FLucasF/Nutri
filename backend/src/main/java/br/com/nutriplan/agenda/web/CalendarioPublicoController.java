package br.com.nutriplan.agenda.web;

import br.com.nutriplan.agenda.service.AssinaturaDaAgendaService;
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
 * O feed iCalendar da agenda.
 *
 * Sem autenticacao: um calendario que assina um endereco nao sabe mandar
 * cabecalho de token. A autorizacao e a posse do endereco, um UUID — a mesma do
 * plano publico, com a mesma ressalva de que quem o recebe, ve.
 */
@RestController
@RequestMapping("/api/publico/agenda")
@RequiredArgsConstructor
@Tag(name = "Assinatura da agenda")
public class CalendarioPublicoController {

    private final AssinaturaDaAgendaService assinaturaService;

    @GetMapping(value = "/{token}.ics", produces = "text/calendar;charset=UTF-8")
    @Operation(summary = "Calendário da agenda, para assinar no Google, Apple ou Outlook",
            description = "Um mês para trás e seis para frente. O calendário busca este "
                    + "endereço de tempos em tempos e reflete o que mudou.")
    public ResponseEntity<byte[]> calendario(@PathVariable String token) {
        String ics = assinaturaService.calendarioDe(token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"agenda.ics\"")
                .body(ics.getBytes(StandardCharsets.UTF_8));
    }
}
