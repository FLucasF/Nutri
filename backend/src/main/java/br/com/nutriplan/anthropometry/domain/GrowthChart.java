package br.com.nutriplan.anthropometry.domain;

import br.com.nutriplan.patient.domain.Sex;
import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One point of the WHO growth curve: the LMS parameters of an indicator, for
 * one sex, at an age in months.
 *
 * Storing L, M and S — and not the table of cutoff points — is what makes it
 * possible to calculate the exact z-score instead of only saying which band the
 * value fell into.
 */
@Entity
@Table(name = "growth_chart",
        uniqueConstraints = @UniqueConstraint(name = "uk_curva",
                columnNames = {"indicator", "sex", "month_number"}),
        indexes = @Index(name = "ix_chart_search", columnList = "indicator, sex, month_number"))
@Getter
@Setter
@NoArgsConstructor
public class GrowthChart extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GrowthIndicator indicator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sex sex;

    @Column(name = "month_number", nullable = false)
    private Integer month;

    /** Skewness of the distribution (Box-Cox). */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal l;

    /** Median of the indicator at that age. */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal m;

    /** Coefficient of variation. */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal s;

    public GrowthChart(GrowthIndicator indicator, Sex sex, Integer month,
                              BigDecimal l, BigDecimal m, BigDecimal s) {
        this.indicator = indicator;
        this.sex = sex;
        this.month = month;
        this.l = l;
        this.m = m;
        this.s = s;
    }

    /**
     * WHO reference this point belongs to.
     *
     * Up to 60 months the 2006 standards hold, built from children in ideal
     * growth conditions; above that, the 2007 reference, which is a
     * reconstruction of the NCHS reference. They are different works, and the
     * distinction has to show up in the result.
     */
    public String reference() {
        return month <= 60 ? "OMS 2006 (0 a 5 anos)" : "OMS 2007 (5 a 19 anos)";
    }
}
