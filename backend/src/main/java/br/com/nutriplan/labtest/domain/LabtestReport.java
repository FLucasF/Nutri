package br.com.nutriplan.labtest.domain;

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
 * The report file, in a table of its own.
 *
 * Separated from {@link Labtest} on purpose: if the binary sat in the same
 * table, every listing of results would drag the files along. Here it is only
 * read when somebody asks for the download.
 *
 * It does not extend BaseEntity because the key is the lab test's own id — the
 * relation is one to one, and a synthetic key would only add an unused column.
 */
@Entity
@Table(name = "labtest_report")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LabtestReport {

    @Id
    @Column(name = "labtest_id")
    private Long labtestId;

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

    public LabtestReport(Long labtestId, byte[] content) {
        this.labtestId = labtestId;
        this.content = content;
    }
}
