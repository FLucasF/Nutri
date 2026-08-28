package br.com.nutriplan.schedule.web;

import br.com.nutriplan.schedule.domain.AppointmentStatus;
import br.com.nutriplan.schedule.domain.AppointmentType;
import br.com.nutriplan.schedule.dto.ScheduleDtos;
import br.com.nutriplan.schedule.service.AppointmentService;
import br.com.nutriplan.schedule.service.ScheduleSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
@Tag(name = "Agenda")
public class ScheduleController {

    private final AppointmentService appointmentService;
    private final ScheduleSubscriptionService subscriptionService;

    @GetMapping("/types")
    @Operation(summary = "Lista os tipos de atendimento e a duração que cada um sugere")
    public List<ScheduleDtos.TypeResponse> types() {
        return Arrays.stream(AppointmentType.values())
                .map(t -> new ScheduleDtos.TypeResponse(
                        t, t.getDescription(), t.getDurationSuggestedMinutes()))
                .toList();
    }

    @GetMapping("/day")
    @Operation(summary = "Agenda de um dia, com o resumo de realizados e faltas")
    public ScheduleDtos.ScheduleDayResponse forDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.forDay(date);
    }

    @GetMapping
    @Operation(summary = "Atendimentos de um período")
    public List<ScheduleDtos.AppointmentResponse> inRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.inRange(from, to, status);
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Histórico de atendimentos de um paciente, incluindo faltas")
    public List<ScheduleDtos.AppointmentResponse> forPatient(@PathVariable Long patientId) {
        return appointmentService.forPatient(patientId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um atendimento")
    public ScheduleDtos.AppointmentResponse detail(@PathVariable Long id) {
        return appointmentService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda um atendimento",
            description = "Recusa horário que se sobreponha a outro atendimento não cancelado.")
    public ScheduleDtos.AppointmentResponse schedule(
            @Valid @RequestBody ScheduleDtos.AppointmentRequest req) {
        return appointmentService.schedule(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Remarca um atendimento")
    public ScheduleDtos.AppointmentResponse reschedule(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleDtos.AppointmentRequest req) {
        return appointmentService.reschedule(id, req);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Registra o desfecho do atendimento",
            description = "Situações terminais não retrocedem: um atendimento realizado "
                    + "não volta a agendado.")
    public ScheduleDtos.AppointmentResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleDtos.StatusChangeRequest req) {
        return appointmentService.changeStatus(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um atendimento")
    public void remove(@PathVariable Long id) {
        appointmentService.remove(id);
    }

    // ---------------------------------------------------- subscription (ics)

    @GetMapping("/subscription")
    @Operation(summary = "Endereço da assinatura iCalendar da agenda",
            description = "Vazio enquanto não gerado. Quem recebe o endereço vê a agenda, "
                    + "com nome de paciente — a mesma autorização por posse de link do plano.")
    public ScheduleSubscriptionService.Subscription subscription() {
        return subscriptionService.current();
    }

    @PostMapping("/subscription")
    @Operation(summary = "Gera ou regenera a assinatura",
            description = "Regerar invalida o endereço que já foi entregue ao calendário.")
    public ScheduleSubscriptionService.Subscription generateSubscription() {
        return subscriptionService.generate();
    }

    @DeleteMapping("/subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desliga a assinatura")
    public void revokeSubscription() {
        subscriptionService.revoke();
    }
}
