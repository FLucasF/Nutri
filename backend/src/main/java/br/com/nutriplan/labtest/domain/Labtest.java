package br.com.nutriplan.labtest.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One lab test result for a patient.
 *
 * It stores the reference range it used, and not only the value. It is the same
 * decision as the protocol stored on the anthropometric assessment and the
 * weight stored on the meal item: the result is a record of what was known on
 * that date, and correcting the catalog later must not rewrite the past.
 */
@Entity
@Table(name = "labtest", indexes = {
        @Index(name = "ix_labtest_account", columnList = "account_id"),
        @Index(name = "ix_labtest_patient", columnList = "patient_id, collection_date")
})
@Getter
@Setter
@NoArgsConstructor
public class Labtest extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_exame_parametro"))
    private LabtestParameter parameter;

    @Column(name = "collection_date", nullable = false)
    private LocalDate dateCollection;

    /**
     * Null means a parameter that was ordered and not yet determined. Never
     * zero: zero is a result, absence is another thing.
     */
    @Column(name = "amount", precision = 12, scale = 3)
    private BigDecimal value;

    @Column(nullable = false, length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private LabtestClassification classification;

    @Column(name = "reference_min", precision = 12, scale = 3)
    private BigDecimal referenceMin;

    @Column(name = "reference_max", precision = 12, scale = 3)
    private BigDecimal referenceMax;

    @Column(length = 1000)
    private String notes;

    @Column(name = "name_report", length = 200)
    private String reportName;

    @Column(name = "report_type", length = 100)
    private String reportType;

    // The binary lives in labtest_report, which no listing touches. Here stay
    // only the name and the type, so that "is there a report?" does not cost a
    // query.

    public Labtest(Long accountId, Long patientId, LabtestParameter parameter, LocalDate dateCollection) {
        this.accountId = accountId;
        this.patientId = patientId;
        this.parameter = parameter;
        this.dateCollection = dateCollection;
        this.unit = parameter.getUnitStandard();
    }

    public boolean hasReport() {
        return reportName != null;
    }

    /** Ready-made text of the range used, to show next to the result. */
    public String referenceAsText() {
        if (referenceMin != null && referenceMax != null) {
            return "%s a %s".formatted(clean(referenceMin), clean(referenceMax));
        }
        if (referenceMax != null) {
            return "até %s".formatted(clean(referenceMax));
        }
        if (referenceMin != null) {
            return "a partir de %s".formatted(clean(referenceMin));
        }
        return null;
    }

    private String clean(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace(".", ",");
    }
}
