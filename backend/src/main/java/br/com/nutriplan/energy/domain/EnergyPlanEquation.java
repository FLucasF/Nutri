package br.com.nutriplan.energy.domain;

import java.math.BigDecimal;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One equation applied inside an energy plan, with the number it gave.
 *
 * The result is stored and not recomputed on read. The plan is what was
 * prescribed on a date, from a weight and an age that were true then; a
 * recalculation later would quietly answer a different question and call it
 * the same record.
 */
@Entity
@Table(name = "energy_plan_equation",
        indexes = @Index(name = "ix_energy_equation_plan", columnList = "plan_id"))
@Getter
@Setter
@NoArgsConstructor
public class EnergyPlanEquation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private EnergyPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EnergyEquation equation;

    /** Basal expenditure. Null for an equation that answers the daily total. */
    @Column(name = "basal_kcal", precision = 8, scale = 2)
    private BigDecimal basalKcal;

    /** Daily expenditure with activity counted. It is what enters the average. */
    @Column(name = "total_kcal", precision = 8, scale = 2, nullable = false)
    private BigDecimal totalKcal;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public EnergyPlanEquation(EnergyEquation equation, BigDecimal basalKcal,
                              BigDecimal totalKcal, Integer order) {
        this.equation = equation;
        this.basalKcal = basalKcal;
        this.totalKcal = totalKcal;
        this.order = order;
    }
}
