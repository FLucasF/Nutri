package br.com.nutriplan.anamnesis.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A labelled field the practice wants on every anamnesis.
 *
 * The client asked for this after reading a proposal with a single "Objetivo"
 * field: he wants to choose the points himself — "um textbox com um label
 * informando o propósito da consulta". So the list belongs to the practice,
 * not to the system.
 *
 * It is not a form builder. There is a label, an order, and whether the value
 * shows in the listing. No types, no validation rules, no conditional logic:
 * the module that already does all that is the questionnaire, and building a
 * second one here would be two machines for the same job.
 */
@Entity
@Table(name = "anamnesis_field",
        indexes = @Index(name = "ix_anamnesis_field_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class AnamnesisField extends AccountEntity {

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer order;

    /**
     * Whether the value appears in the patient's anamnesis listing.
     *
     * This is what answers the ask: seeing the purpose of the visit without
     * opening the record.
     */
    @Column(name = "show_in_listing", nullable = false)
    private boolean showInListing = true;

    @Column(nullable = false)
    private boolean active = true;

    public AnamnesisField(Long accountId, String label, Integer order, boolean showInListing) {
        setAccountId(accountId);
        this.label = label;
        this.order = order;
        this.showInListing = showInListing;
    }
}
