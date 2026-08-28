package br.com.nutriplan.questionario.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resposta a uma pergunta.
 *
 * Guarda o enunciado, e não só a chave da pergunta. Se o nutricionista remover
 * uma pergunta do modelo depois, a resposta antiga não pode perder o enunciado:
 * o paciente respondeu àquela pergunta, e não à versão atual do formulário.
 */
@Entity
@Table(name = "resposta_item",
        indexes = @Index(name = "ix_item_resposta", columnList = "resposta_id"))
@Getter
@Setter
@NoArgsConstructor
public class ItemDeResposta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resposta_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_resposta"))
    private RespostaDeQuestionario resposta;

    /** Procedência. Fica nulo se a pergunta for removida do modelo. */
    @Column(name = "pergunta_id")
    private Long perguntaId;

    @Column(name = "pergunta_texto", nullable = false, length = 500)
    private String perguntaTexto;

    @Column(length = 2000)
    private String valor;

    @Column
    private Integer pontos;

    @Column(nullable = false)
    private Integer ordem;

    public ItemDeResposta(RespostaDeQuestionario resposta, Pergunta pergunta, String valor) {
        this.resposta = resposta;
        this.perguntaId = pergunta.getId();
        this.perguntaTexto = pergunta.getEnunciado();
        this.valor = valor;
        this.ordem = pergunta.getOrdem();
    }
}
