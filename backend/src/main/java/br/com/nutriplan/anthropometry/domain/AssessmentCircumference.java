package br.com.nutriplan.anthropometry.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One circumference of one assessment, at one site and one side. */
@Entity
@Table(name = "assessment_circumference",
        indexes = @Index(name = "ix_circumference_assessment", columnList = "assessment_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_circumference_site",
                columnNames = {"assessment_id", "site", "side"}))
@Getter
@Setter
@NoArgsConstructor
public class AssessmentCircumference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private AnthropometricAssessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CircumferenceSite site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Side side = Side.SINGLE;

    @Column(name = "value_cm", precision = 6, scale = 2, nullable = false)
    private BigDecimal valueCm;

    public AssessmentCircumference(CircumferenceSite site, Side side, BigDecimal valueCm) {
        this.site = site;
        this.side = side;
        this.valueCm = valueCm;
    }
}
