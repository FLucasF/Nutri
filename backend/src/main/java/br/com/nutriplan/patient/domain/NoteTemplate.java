package br.com.nutriplan.patient.domain;

import br.com.nutriplan.shared.domain.AccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um texto-base para a anotação do paciente: "primeira consulta", "retorno".
 *
 * O cliente pediu para salvar modelos e começar a anotação de qualquer
 * paciente a partir deles, com a mesma formatação. O corpo é o documento do
 * editor, o mesmo que a anotação guarda — copiar é colar, sem conversão.
 */
@Entity
@Table(name = "note_template",
        indexes = @Index(name = "ix_note_template_account", columnList = "account_id, name"))
@Getter
@Setter
@NoArgsConstructor
public class NoteTemplate extends AccountEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 8000)
    private String body;

    public NoteTemplate(Long accountId, String name, String body) {
        setAccountId(accountId);
        this.name = name;
        this.body = body;
    }
}
