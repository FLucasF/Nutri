package br.com.nutriplan.labtest.domain;

import br.com.nutriplan.patient.domain.Sex;
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

/**
 * Reference range of a parameter, by sex and age band.
 *
 * A null end means "no limit on that side": LDL has no minimum of concern, and
 * the reference value is "below 130".
 */
@Entity
@Table(name = "reference_range",
        indexes = @Index(name = "ix_range_parameter", columnList = "parameter_id"))
@Getter
@Setter
@NoArgsConstructor
public class ReferenceRange extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_faixa_parametro"))
    private LabtestParameter parameter;

    /** Null holds for both sexes. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @Column(name = "age_min")
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    @Column(precision = 12, scale = 3)
    private BigDecimal minimum;

    @Column(precision = 12, scale = 3)
    private BigDecimal maximum;

    /** Does the range serve the patient described? A null sex and a null age do not restrict. */
    public boolean serve(Sex patientSex, Integer age) {
        if (sex != null && sex != patientSex) {
            return false;
        }
        if (ageMin != null && (age == null || age < ageMin)) {
            return false;
        }
        return ageMax == null || (age != null && age <= ageMax);
    }

    /** The most specific range wins: the one restricting sex counts more than the general one. */
    public int specificity() {
        int points = 0;
        if (sex != null) points++;
        if (ageMin != null || ageMax != null) points++;
        return points;
    }

    public String asText() {
        if (minimum != null && maximum != null) {
            return "%s a %s".formatted(clean(minimum), clean(maximum));
        }
        if (maximum != null) {
            return "até %s".formatted(clean(maximum));
        }
        return "a partir de %s".formatted(clean(minimum));
    }

    private String clean(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace(".", ",");
    }
}
