package br.com.nutriplan.servicepackage.dto;

import br.com.nutriplan.servicepackage.domain.ServicePackage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Contratos dos pacotes de trabalho. */
public final class PackageDtos {

    private PackageDtos() {
    }

    public record PackageRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
            BigDecimal amount,
            @Min(value = 1, message = "Um pacote tem ao menos um encontro")
            @Max(value = 200, message = "Encontros demais para um pacote")
            Integer sessions,
            @Min(value = 1, message = "O intervalo mínimo é de um dia")
            @Max(value = 365, message = "O intervalo máximo é de um ano")
            Integer intervalDays,
            @Size(max = 500) String notes
    ) {}

    public record PackageResponse(
            Long id,
            String name,
            BigDecimal amount,
            Integer sessions,
            Integer intervalDays,
            String notes,
            boolean active
    ) {
        public static PackageResponse from(ServicePackage p) {
            return new PackageResponse(p.getId(), p.getName(), p.getAmount(), p.getSessions(),
                    p.getIntervalDays(), p.getNotes(), p.isActive());
        }
    }
}
