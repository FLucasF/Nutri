package br.com.nutriplan.auth.domain;

/** Papeis de acesso. Mapeados para authorities do Spring com prefixo ROLE_. */
public enum Perfil {
    /** Dono da conta: acesso total ao proprio consultorio. */
    NUTRICIONISTA,
    /** Acesso operacional (agenda, cadastro), sem prescricao nem financeiro. */
    SECRETARIA,
    /** Paciente, via aplicativo: enxerga apenas os proprios dados. */
    PACIENTE,
    /** Administrador da plataforma. */
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
