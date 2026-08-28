package br.com.nutriplan.auth.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Unit of data isolation (tenant). Every clinical record belongs to exactly one
 * account; queries are always filtered by it.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.EXPERIMENTAL;

    @Column(name = "plan_expires_at")
    private LocalDate planExpiresAt;

    /** Primary color used in the patient's app and in the PDFs. */
    @Column(name = "primary_color", length = 7)
    private String primaryColor = "#2E7D5B";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private boolean active = true;

    public Account(String name) {
        this.name = name;
        this.plan = Plan.EXPERIMENTAL;
        this.planExpiresAt = LocalDate.now().plusDays(Plan.EXPERIMENTAL.testeDays());
    }

    public boolean planCurrent() {
        return active && (planExpiresAt == null || !planExpiresAt.isBefore(LocalDate.now()));
    }

    /**
     * Address of the schedule's iCalendar subscription.
     *
     * Null until the nutritionist asks for it. It is a UUID for the same reason
     * as the public plan — a sequential id would let anyone subscribe to the
     * neighbour's schedule by adding 1 — and the same caveat holds: whoever
     * receives the link, sees.
     */
    @jakarta.persistence.Column(name = "schedule_token", length = 36)
    private String tokenSchedule;

    /** Generates or regenerates the subscription. Regenerating invalidates the previous address. */
    public String generateScheduleToken() {
        this.tokenSchedule = java.util.UUID.randomUUID().toString();
        return this.tokenSchedule;
    }
}
