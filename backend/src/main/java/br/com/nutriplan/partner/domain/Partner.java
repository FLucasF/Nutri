package br.com.nutriplan.partner.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quem indica pacientes ao consultório: um personal, um médico, uma academia,
 * outro paciente.
 *
 * O cadastro existe para o relatório de indicações — "para eventuais
 * benefícios ao indicante". O vínculo fica no paciente (quem o trouxe) e,
 * quando a indicação vale para um atendimento específico, na consulta.
 */
@Entity
@Table(name = "partner",
        indexes = @Index(name = "ix_partner_account", columnList = "account_id, name"))
@Getter
@Setter
@NoArgsConstructor
public class Partner extends AccountEntity {

    @Column(nullable = false, length = 150)
    private String name;

    /** Livre: "Personal", "Médico", "Academia", "Paciente"… */
    @Column(length = 40)
    private String kind;

    @Column(length = 180)
    private String contact;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public Partner(Long accountId, String name) {
        setAccountId(accountId);
        this.name = name;
    }
}
