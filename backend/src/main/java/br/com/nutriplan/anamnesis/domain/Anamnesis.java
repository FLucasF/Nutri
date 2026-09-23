package br.com.nutriplan.anamnesis.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.nutriplan.shared.domain.AccountEntity;
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

/**
 * The general anamnesis — what the client calls the heart of the consultation.
 *
 * Two things live in the same record, and they answer different questions. The
 * body is the consultation written out, in the editor's format. The values are
 * the short labelled points the practice declared, and they are what shows in
 * the listing so the purpose of a visit can be read without opening it.
 *
 * Summarising the free text automatically would fill the same space by
 * guessing. A declared field says what the professional meant; a summariser
 * says what it inferred, and a clinical record is no place for the difference.
 */
@Entity
@Table(name = "anamnesis", indexes = {
        @Index(name = "ix_anamnesis_patient", columnList = "patient_id"),
        @Index(name = "ix_anamnesis_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Anamnesis extends AccountEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    /** The editor's document, as JSON. Null while it is still blank. */
    @Column(length = 40_000)
    private String body;

    @OneToMany(mappedBy = "anamnesis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order")
    private List<AnamnesisValue> values = new ArrayList<>();

    public Anamnesis(Long accountId, Long patientId, String name, LocalDate date) {
        setAccountId(accountId);
        this.patientId = patientId;
        this.name = name;
        this.date = date;
    }

    public void clearValues() {
        values.clear();
    }

    public void add(AnamnesisValue value) {
        value.setAnamnesis(this);
        values.add(value);
    }
}
