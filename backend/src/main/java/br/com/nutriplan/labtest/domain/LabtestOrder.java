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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The lab test order the nutritionist hands to the patient. */
@Entity
@Table(name = "labtest_order",
        indexes = @Index(name = "ix_order_patient", columnList = "patient_id, date"))
@Getter
@Setter
@NoArgsConstructor
public class LabtestOrder extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderedParameter> parameters = new ArrayList<>();

    public LabtestOrder(Long accountId, Long patientId, LocalDate date) {
        this.accountId = accountId;
        this.patientId = patientId;
        this.date = date;
    }
}
