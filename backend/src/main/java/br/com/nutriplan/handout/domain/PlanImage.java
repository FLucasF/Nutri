package br.com.nutriplan.handout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * The copy of the image delivered in a plan.
 *
 * It is a copy, and not a reference to the library image, for the same reason
 * as the text: changing the figure in the template later would change what
 * dozens of patients have already received. The cost is a few hundred KB per
 * plan; the alternative is a delivered plan that changes on its own.
 */
@Entity
@Table(name = "plan_image")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class PlanImage {

    @Id
    @Column(name = "plan_id_handout")
    private Long planIdHandout;

    @Column(nullable = false)
    private byte[] content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 180)
    private String updatedBy;

    public PlanImage(Long planIdHandout, byte[] content) {
        this.planIdHandout = planIdHandout;
        this.content = content;
    }
}
