package br.com.nutriplan.patient.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "patient", indexes = {
        @Index(name = "ix_patient_account", columnList = "account_id"),
        @Index(name = "ix_patient_account_name", columnList = "account_id, name")
})
@Getter
@Setter
@NoArgsConstructor
public class Patient extends AccountEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 180)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate dateBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sex sex;

    @Column(length = 20)
    private String cpf;

    /** Como ele chama o paciente. Opcional, e não substitui o nome. */
    @Column(length = 80)
    private String nickname;

    /**
     * Condição biológica, quando há uma.
     *
     * Só se aplica a mulher. A ausência já diz "nenhuma das duas", que é por
     * isso que não existe um valor para isso.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "biological_condition", length = 20)
    private BiologicalCondition biologicalCondition;

    @Column(length = 100)
    private String occupation;

    @Column(name = "goal", length = 500)
    private String goal;

    @Column(length = 8000)
    private String notes;

    /** A user of the patient app, created on demand when access is granted. */
    @Column(name = "user_id")
    private Long userId;

    /** Quem indicou o paciente ao consultório, quando foi indicação. */
    @Column(name = "partner_id")
    private Long partnerId;

    @Column(nullable = false)
    private boolean active = true;

    public Patient(Long accountId, String name) {
        setAccountId(accountId);
        this.name = name;
    }

    /**
     * Age in completed years at the reference date. Delegated to Period so as
     * to get leap years and birthdays on 29 February right.
     */
    public Integer ageAt(LocalDate reference) {
        if (dateBirth == null || reference == null || dateBirth.isAfter(reference)) {
            return null;
        }
        return Period.between(dateBirth, reference).getYears();
    }

    public Integer getAge() {
        return ageAt(LocalDate.now());
    }

    public boolean hasAccessToApp() {
        return userId != null;
    }
}
