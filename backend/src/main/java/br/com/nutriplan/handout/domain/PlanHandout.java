package br.com.nutriplan.handout.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A handout as a patient received it, attached to a plan.
 *
 * It keeps the text, and not a pointer to the library. The copy is the point:
 * if the plan merely pointed, correcting a template would silently change what
 * dozens of patients have already received — including a closed plan, which is
 * the record of what was prescribed. It is the same rule as the weight stored
 * in the meal item.
 *
 * The copy is also what allows personalizing: the nutritionist adapts the text
 * for that patient without dirtying the template.
 */
@Entity
@Table(name = "plan_handout",
        indexes = @Index(name = "ix_handout_plan", columnList = "plan_id"))
@Getter
@Setter
@NoArgsConstructor
public class PlanHandout extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** Provenance, not a dependency: it can stay null without affecting the text. */
    @Column(name = "handout_id")
    private Long handoutId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 8000)
    private String body;

    /** Name and type here; the binary in plan_image. */
    @Column(name = "name_image", length = 200)
    private String imageName;

    @Column(name = "image_type", length = 100)
    private String imageType;

    public boolean hasImage() {
        return imageName != null;
    }

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    public PlanHandout(Long planId, String title, String body, int order) {
        this.planId = planId;
        this.title = title;
        this.body = body;
        this.order = order;
    }
}
