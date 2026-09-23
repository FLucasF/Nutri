package br.com.nutriplan.patient.domain;

/**
 * O que o anexo é.
 *
 * Arquivo e link ficam na mesma tabela porque, para quem usa, são a mesma
 * coisa: algo do paciente que fica guardado junto do prontuário. Separar em
 * duas telas obrigaria a saber de antemão em qual procurar.
 */
public enum AttachmentKind {

    FILE("Arquivo"),
    LINK("Link");

    private final String description;

    AttachmentKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
