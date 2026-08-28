package br.com.nutriplan.questionario.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "pergunta",
        indexes = @Index(name = "ix_pergunta_questionario", columnList = "questionario_id"))
@Getter
@Setter
@NoArgsConstructor
public class Pergunta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questionario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pergunta_questionario"))
    private Questionario questionario;

    @Column(nullable = false, length = 500)
    private String enunciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDePergunta tipo;

    @Column(nullable = false)
    private boolean obrigatoria = false;

    @Column(nullable = false)
    private Integer ordem;

    /** Alternativas, com a pontuação de cada uma quando o formulário pontua. */
    @Column(length = 2000)
    private String opcoes;

    @Column(length = 500)
    private String ajuda;

    public Pergunta(Questionario questionario, String enunciado, TipoDePergunta tipo, int ordem) {
        this.questionario = questionario;
        this.enunciado = enunciado;
        this.tipo = tipo;
        this.ordem = ordem;
    }

    public List<Opcao> opcoesAnalisadas() {
        return Opcao.analisar(opcoes);
    }
}
