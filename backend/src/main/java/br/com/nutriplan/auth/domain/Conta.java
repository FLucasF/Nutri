package br.com.nutriplan.auth.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Unidade de isolamento de dados (tenant). Todo registro clinico pertence a
 * exatamente uma conta; consultas sao sempre filtradas por ela.
 */
@Entity
@Table(name = "conta")
@Getter
@Setter
@NoArgsConstructor
public class Conta extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plano plano = Plano.EXPERIMENTAL;

    @Column(name = "plano_expira_em")
    private LocalDate planoExpiraEm;

    /** Cor primaria usada no app do paciente e nos PDFs. */
    @Column(name = "cor_primaria", length = 7)
    private String corPrimaria = "#2E7D5B";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false)
    private boolean ativa = true;

    public Conta(String nome) {
        this.nome = nome;
        this.plano = Plano.EXPERIMENTAL;
        this.planoExpiraEm = LocalDate.now().plusDays(Plano.EXPERIMENTAL.diasDeTeste());
    }

    public boolean planoVigente() {
        return ativa && (planoExpiraEm == null || !planoExpiraEm.isBefore(LocalDate.now()));
    }

    /**
     * Endereço da assinatura iCalendar da agenda.
     *
     * Nulo enquanto o nutricionista não pede. É um UUID pela mesma razão do
     * plano público — um id sequencial permitiria assinar a agenda do vizinho
     * somando 1 — e vale a mesma ressalva: quem recebe o link, vê.
     */
    @jakarta.persistence.Column(name = "token_agenda", length = 36)
    private String tokenAgenda;

    /** Gera ou regenera a assinatura. Regenerar invalida o endereço anterior. */
    public String gerarTokenDaAgenda() {
        this.tokenAgenda = java.util.UUID.randomUUID().toString();
        return this.tokenAgenda;
    }
}
