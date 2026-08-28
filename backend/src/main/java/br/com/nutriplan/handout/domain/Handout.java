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
 * A handout text in the practice's library.
 *
 * It exists so that the nutritionist does not rewrite "how to build your plate"
 * for every patient. The catalog follows the same axis as the foods and the
 * household measures: a null account identifies a system template, common to
 * everyone; a filled-in account, a practice's text, visible only to it.
 */
@Entity
@Table(name = "handout", indexes = @Index(name = "ix_handout_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class Handout extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 8000)
    private String body;

    /** Name and type here; the binary in handout_image. */
    @Column(name = "name_image", length = 200)
    private String imageName;

    @Column(name = "image_type", length = 100)
    private String imageType;

    public boolean hasImage() {
        return imageName != null;
    }

    @Column(nullable = false)
    private boolean active = true;

    public Handout(Long accountId, String title, String body) {
        this.accountId = accountId;
        this.title = title;
        this.body = body;
    }

    /** A system template: it serves as a starting point, and nobody edits it. */
    public boolean isSystemTemplate() {
        return accountId == null;
    }
}
