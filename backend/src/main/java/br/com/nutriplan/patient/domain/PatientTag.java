package br.com.nutriplan.patient.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma TAG que aglutina pacientes.
 *
 * "A TAG do paciente deverá funcionar como um aglutinador dos pacientes."
 * É ela que resolve o que ele queria do avatar colorido — varrer a lista sem
 * ler nome. Duas cores por sexo dão dois grupos; TAG dá quantos ele criar.
 *
 * Conta nula identifica as que o sistema traz como ponto de partida.
 */
@Entity
@Table(name = "patient_tag",
        indexes = @Index(name = "ix_patient_tag_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class PatientTag extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    public PatientTag(Long accountId, String name) {
        this.accountId = accountId;
        this.name = name;
    }

    public boolean isSystemTag() {
        return accountId == null;
    }
}
