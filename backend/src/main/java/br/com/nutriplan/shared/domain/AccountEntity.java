package br.com.nutriplan.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base of the entities that belong to a practice.
 *
 * The link is stored as a raw id, and not as a @ManyToOne: the account is used
 * only as a filter in every query, so materializing the Account entity would
 * only produce a join or a lazy-load with no use. Integrity is guaranteed by
 * the foreign key declared in the migration.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class AccountEntity extends BaseEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    public boolean belongs(Long account) {
        return accountId != null && accountId.equals(account);
    }
}
