package br.com.nutriplan.prescription.domain;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The photo of a meal, as the client asked on page 33.
 *
 * "Adicionar uma foto, eu poderia subir uma/umas foto de como eu quero que
 * fique e aparecerá lá no PDF."
 *
 * The bytes live apart from the meal for the same reason the handout's image
 * does: every read of a plan loads its meals, and carrying a few hundred
 * kilobytes of JPEG through that read would make the editor slower for the
 * many meals that have no photo at all.
 */
@Entity
@Table(name = "meal_photo")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class MealPhoto {

    @Id
    @Column(name = "meal_id")
    private Long mealId;

    @Column(nullable = false)
    private byte[] content;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    public MealPhoto(Long mealId, byte[] content) {
        this.mealId = mealId;
        this.content = content;
    }
}
