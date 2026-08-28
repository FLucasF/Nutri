package br.com.nutriplan.auth.domain;

/** Access roles. Mapped to Spring authorities with the ROLE_ prefix. */
public enum Role {
    /** Owner of the account: full access to their own practice. */
    NUTRITIONIST,
    /** Operational access (schedule, registration), without prescription or finance. */
    ASSISTANT,
    /** Patient, through the app: they see only their own data. */
    PATIENT,
    /** Administrador da plataforma. */
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
