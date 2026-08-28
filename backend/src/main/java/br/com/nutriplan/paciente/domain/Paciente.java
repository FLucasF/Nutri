package br.com.nutriplan.paciente.domain;

import br.com.nutriplan.shared.domain.EntidadeDeConta;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "paciente", indexes = {
        @Index(name = "ix_paciente_conta", columnList = "conta_id"),
        @Index(name = "ix_paciente_conta_nome", columnList = "conta_id, nome")
})
@Getter
@Setter
@NoArgsConstructor
public class Paciente extends EntidadeDeConta {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 180)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sexo sexo;

    @Column(length = 20)
    private String cpf;

    @Column(length = 100)
    private String profissao;

    @Column(name = "objetivo", length = 500)
    private String objetivo;

    @Column(length = 2000)
    private String observacoes;

    /** Usuario do app do paciente, criado sob demanda ao conceder acesso. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private boolean ativo = true;

    public Paciente(Long contaId, String nome) {
        setContaId(contaId);
        this.nome = nome;
    }

    /**
     * Idade em anos completos na data de referencia. Delegado a Period para
     * acertar anos bissextos e aniversarios em 29/02.
     */
    public Integer idadeEm(LocalDate referencia) {
        if (dataNascimento == null || referencia == null || dataNascimento.isAfter(referencia)) {
            return null;
        }
        return Period.between(dataNascimento, referencia).getYears();
    }

    public Integer getIdade() {
        return idadeEm(LocalDate.now());
    }

    public boolean temAcessoAoApp() {
        return usuarioId != null;
    }
}
