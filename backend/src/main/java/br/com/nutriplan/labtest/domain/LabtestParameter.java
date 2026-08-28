package br.com.nutriplan.labtest.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * A laboratory parameter: glycemia, ferritin, TSH.
 *
 * A null account identifies the system catalog, common to every practice; a
 * filled-in one, a parameter of the practice's own — the same axis as the food
 * catalog.
 */
@Entity
@Table(name = "labtest_parameter",
        indexes = @Index(name = "ix_parameter_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class LabtestParameter extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 120)
    private String name;

    /**
     * Unit in which the catalog expects the value. It serves to compare series:
     * a value in another unit does not enter the same timeline.
     */
    @Column(name = "standard_unit", nullable = false, length = 20)
    private String unitStandard;

    @Column(name = "group_name", length = 60)
    private String group;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "parameter", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ReferenceRange> ranges = new ArrayList<>();

    public LabtestParameter(Long accountId, String name, String unitStandard, String group) {
        this.accountId = accountId;
        this.name = name;
        this.unitStandard = unitStandard;
        this.group = group;
    }

    public boolean isDoSystemCatalog() {
        return accountId == null;
    }
}
