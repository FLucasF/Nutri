package br.com.nutriplan.partner.dto;

import br.com.nutriplan.partner.domain.Partner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contratos dos parceiros de indicação e do seu relatório. */
public final class PartnerDtos {

    private PartnerDtos() {
    }

    public record PartnerRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 40) String kind,
            @Size(max = 180) String contact,
            @Size(max = 1000) String notes
    ) {}

    public record PartnerResponse(
            Long id,
            String name,
            String kind,
            String contact,
            String notes,
            boolean active,
            /** Quantos pacientes do consultório foram indicados por ele. */
            long patientsReferred
    ) {
        public static PartnerResponse from(Partner p, long patientsReferred) {
            return new PartnerResponse(p.getId(), p.getName(), p.getKind(), p.getContact(),
                    p.getNotes(), p.isActive(), patientsReferred);
        }
    }

    /**
     * Uma linha do relatório de indicações.
     *
     * A consulta conta para o parceiro marcado nela ou, sem marcação, para o
     * parceiro que indicou o paciente. A receita é a paga no período, atribuída
     * do mesmo jeito: pela consulta a que se refere ou pelo paciente.
     */
    public record ReferralRow(
            Long partnerId,
            String partnerName,
            String kind,
            long patientsReferred,
            long appointments,
            long completed,
            long noshows,
            BigDecimal revenuePaid
    ) {}

    public record ReferralReport(
            LocalDate from,
            LocalDate to,
            List<ReferralRow> rows,
            long totalPatients,
            long totalAppointments,
            BigDecimal totalRevenue
    ) {}
}
