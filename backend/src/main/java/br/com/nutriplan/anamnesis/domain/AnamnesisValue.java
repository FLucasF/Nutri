package br.com.nutriplan.anamnesis.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What was written in one of the practice's fields, on one anamnesis.
 *
 * It keeps the label as well as the field id, on purpose. Renaming "Objetivo"
 * to "Meta" next year must not rewrite what last year's record says: the
 * anamnesis is the record of a consultation that happened on a date, and the
 * label is part of what was asked that day. Same reason the questionnaire
 * answer keeps the question's wording, and the lab test keeps its reference
 * range.
 */
@Entity
@Table(name = "anamnesis_value",
        indexes = @Index(name = "ix_anamnesis_value_anamnesis", columnList = "anamnesis_id"))
@Getter
@Setter
@NoArgsConstructor
public class AnamnesisValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anamnesis_id", nullable = false)
    private Anamnesis anamnesis;

    /** Null once the field is removed from the practice's list. */
    @Column(name = "field_id")
    private Long fieldId;

    @Column(nullable = false, length = 120)
    private String label;

    /** The column is {@code content} because {@code value} is reserved in H2. */
    @Column(name = "content", length = 500)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public AnamnesisValue(Long fieldId, String label, String value, Integer order) {
        this.fieldId = fieldId;
        this.label = label;
        this.value = value;
        this.order = order;
    }

    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
}
