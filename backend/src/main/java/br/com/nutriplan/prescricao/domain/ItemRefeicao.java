package br.com.nutriplan.prescricao.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import br.com.nutriplan.shared.util.MedidaNoPlural;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Um item prescrito dentro de uma refeicao.
 *
 * O item guarda tres coisas que parecem redundantes e nao sao:
 *
 *  - a porcao escolhida e a quantidade ("3 colheres de sopa"), que e como o
 *    paciente le a prescricao;
 *  - o peso em gramas resultante, que e sobre o que o calculo nutricional roda;
 *  - a descricao do alimento no momento da prescricao.
 *
 * O peso e gravado, e nao derivado a cada leitura, porque a porcao pode ser
 * corrigida depois: se o consultorio ajustar sua "colher de sopa" de 25 g para
 * 28 g, um plano ja entregue ao paciente nao pode se reescrever sozinho. O que
 * foi prescrito naquele dia continua sendo o que foi prescrito.
 *
 * Em plano qualitativo a quantidade e o peso ficam nulos: "salada a vontade"
 * nao tem numero, e forcar um seria inventar dado clinico.
 */
@Entity
@Table(name = "item_refeicao", indexes = {
        @Index(name = "ix_item_refeicao", columnList = "refeicao_id"),
        @Index(name = "ix_item_alimento", columnList = "alimento_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ItemRefeicao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refeicao_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_item_refeicao"))
    private Refeicao refeicao;

    /** Nulo em item puramente textual, usado no metodo qualitativo. */
    @Column(name = "alimento_id")
    private Long alimentoId;

    /** Porcao escolhida. Nulo quando a prescricao foi feita direto em gramas. */
    @Column(name = "medida_id")
    private Long medidaId;

    /**
     * Como o alimento aparece no plano. Copiado do alimento na prescricao, e
     * editavel: o profissional pode escrever "arroz integral (do almoco)" para
     * o paciente, sem alterar o cadastro do alimento.
     */
    @Column(nullable = false, length = 250)
    private String descricao;

    /** Rotulo da porcao no momento da prescricao, ex.: "colher de sopa cheia". */
    @Column(name = "descricao_medida", length = 120)
    private String descricaoMedida;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantidade;

    /** Peso resultante, congelado na edicao. Base do calculo nutricional. */
    @Column(precision = 10, scale = 3)
    private BigDecimal gramas;

    @Column(nullable = false)
    private Integer ordem;

    @Column(length = 500)
    private String observacao;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<EquivalenteItem> equivalentes = new ArrayList<>();

    public ItemRefeicao(String descricao) {
        this.descricao = descricao;
    }

    public void adicionarEquivalente(EquivalenteItem equivalente) {
        equivalente.setItem(this);
        equivalentes.add(equivalente);
    }

    /** Item com peso conhecido entra na totalizacao; sem peso, nao. */
    public boolean entraNoCalculo() {
        return alimentoId != null && gramas != null && gramas.signum() > 0;
    }

    /** Texto pronto da porcao, do jeito que o paciente le. */
    public String porcaoFormatada() {
        if (quantidade == null) {
            return descricaoMedida != null ? descricaoMedida : "a vontade";
        }
        String numero = quantidade.stripTrailingZeros().toPlainString();
        if (descricaoMedida != null && !descricaoMedida.isBlank()) {
            return "%s %s".formatted(numero,
                    MedidaNoPlural.concordar(quantidade, descricaoMedida));
        }
        return "%s g".formatted(numero);
    }
}
