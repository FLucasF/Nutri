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
 * The image of a handout in the library.
 *
 * A table of its own for the same reason as the lab report: if the binary sat
 * in `handout`, every listing of the library would drag the images along.
 */
@Entity
@Table(name = "handout_image")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class HandoutImage {

    @Id
    @Column(name = "handout_id")
    private Long handoutId;

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

    public HandoutImage(Long handoutId, byte[] content) {
        this.handoutId = handoutId;
        this.content = content;
    }
}
