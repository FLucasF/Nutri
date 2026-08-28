package br.com.nutriplan.questionnaire.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * A form the practice applies.
 *
 * A null account identifies a system template. Published instruments —
 * metabolic screening, FINDRISC, sleep scales — have licenses of their own, and
 * so the system ships only one generic template of its own authorship: each
 * practice registers the instruments it has the right to use.
 */
@Entity
@Table(name = "questionnaire",
        indexes = @Index(name = "ix_questionnaire_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class Questionnaire extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Name of the published instrument, when it is one of them. */
    @Column(length = 120)
    private String instrument;

    @Column(length = 30)
    private String version;

    @Column(nullable = false)
    private boolean scorable = false;

    @Column(name = "cutoff_range", length = 500)
    private String cutoffRange;

    /**
     * Increments on every edit.
     *
     * The answer keeps the number it answered, and that is what makes it
     * possible to know which form the patient saw without rebuilding it.
     */
    @Column(name = "version_template", nullable = false)
    private int templateVersion = 1;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order asc")
    @BatchSize(size = 50)
    private List<Question> questions = new ArrayList<>();

    public Questionnaire(Long accountId, String name) {
        this.accountId = accountId;
        this.name = name;
    }

    public boolean isSystemTemplate() {
        return accountId == null;
    }
}
