package br.com.nutriplan.servicepackage.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Um pacote de trabalho: "um nome atrelado a um valor". Com número de
 * encontros e intervalo, a agenda marca as próximas consultas de uma vez.
 *
 * Fica ligado à consulta e ao lançamento financeiro, para o relatório dizer
 * quanto cada pacote rendeu — foi o que o cliente pediu dele.
 */
@Entity
@Table(name = "service_package",
        indexes = @Index(name = "ix_service_package_account", columnList = "account_id, name"))
@Getter
@Setter
@NoArgsConstructor
public class ServicePackage extends AccountEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Encontros presenciais incluídos. Nulo quando o pacote não é por sessões. */
    @Column
    private Integer sessions;

    /** Dias entre um encontro e o próximo, para marcar a série. */
    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public ServicePackage(Long accountId, String name, BigDecimal amount) {
        setAccountId(accountId);
        this.name = name;
        this.amount = amount;
    }

    /** Quantas consultas além da primeira o pacote ainda marca. */
    public int followupSessions() {
        return sessions == null || sessions <= 1 ? 0 : sessions - 1;
    }

    public int intervalOrWeekly() {
        return intervalDays == null || intervalDays <= 0 ? 7 : intervalDays;
    }
}
