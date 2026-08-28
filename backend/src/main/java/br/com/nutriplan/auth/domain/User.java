package br.com.nutriplan.auth.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usuario_conta"))
    private Account account;

    /** Council registration (CRN). Only for the NUTRITIONIST role. */
    @Column(length = 30)
    private String crn;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Version of the password, incremented on every change.
     *
     * Authentication is stateless, so there is no session to end. The token
     * carries the version in force at issue; when the password changes, the
     * number changes, and every earlier token stops matching.
     *
     * It is a counter and not a timestamp on purpose: the JWT issue date has
     * second precision, and comparing against an instant with milliseconds
     * produces the two opposite errors — logging out someone who has just
     * changed their own password, or keeping alive the session one wanted to
     * kill.
     */
    @Column(name = "password_version", nullable = false)
    private int passwordVersion = 0;

    /** Auditing: when the password was last changed. */
    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /** Changes the password and drops the open sessions. */
    public void changePassword(String novoHash, java.time.Instant when) {
        this.passwordHash = novoHash;
        this.passwordVersion++;
        this.passwordChangedAt = when;
    }

    public User(String name, String email, String passwordHash, Role role, Account account) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.account = account;
    }
}
