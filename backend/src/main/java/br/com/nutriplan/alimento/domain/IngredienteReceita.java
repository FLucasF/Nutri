package br.com.nutriplan.alimento.domain;

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

import java.math.BigDecimal;

/**
 * Um ingrediente dentro de uma receita.
 *
 * Aponta para dois alimentos: a receita a que pertence e o alimento usado. Os
 * dois sao {@link Alimento} porque uma receita pode entrar em outra — refogado
 * dentro de torta — e nada no modelo precisa saber disso de antemao.
 */
@Entity
@Table(name = "ingrediente_receita", indexes = {
        @Index(name = "ix_ingrediente_receita", columnList = "receita_id"),
        @Index(name = "ix_ingrediente_alimento", columnList = "alimento_id")
})
@Getter
@Setter
@NoArgsConstructor
public class IngredienteReceita extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receita_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ingrediente_receita"))
    private Alimento receita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alimento_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ingrediente_alimento"))
    private Alimento alimento;

    /** Medida caseira escolhida. Nulo quando o ingrediente foi pesado direto. */
    @Column(name = "medida_id")
    private Long medidaId;

    /**
     * Rotulo da medida no momento em que a receita foi montada. Repete o que
     * esta em {@link MedidaCaseira} pelo mesmo motivo do item de refeicao: a
     * medida pode ser corrigida ou removida depois, e a receita continua tendo
     * de dizer "2 colheres de sopa" para quem a le.
     */
    @Column(name = "descricao_medida", length = 120)
    private String descricaoMedida;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    /**
     * Peso do ingrediente, resolvido na gravacao.
     *
     * Guardado, e nao derivado a cada leitura: corrigir depois o peso de uma
     * colher de sopa nao pode alterar em silencio a composicao de uma receita
     * que o nutricionista ja conferiu. E a mesma regra do peso prescrito.
     */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal gramas;

    @Column(nullable = false)
    private Integer ordem;

    public IngredienteReceita(Alimento receita, Alimento alimento, BigDecimal gramas, int ordem) {
        this.receita = receita;
        this.alimento = alimento;
        this.gramas = gramas;
        this.ordem = ordem;
    }

    /** Texto pronto da quantidade, do jeito que aparece na lista de ingredientes. */
    public String quantidadeFormatada() {
        if (quantidade == null) {
            return "%s g".formatted(gramas.stripTrailingZeros().toPlainString());
        }
        String numero = quantidade.stripTrailingZeros().toPlainString();
        if (descricaoMedida != null && !descricaoMedida.isBlank()) {
            return "%s %s".formatted(numero,
                    br.com.nutriplan.shared.util.MedidaNoPlural.concordar(quantidade, descricaoMedida));
        }
        return "%s g".formatted(numero);
    }
}
