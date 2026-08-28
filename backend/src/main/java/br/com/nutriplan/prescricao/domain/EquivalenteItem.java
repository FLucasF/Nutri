package br.com.nutriplan.prescricao.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import br.com.nutriplan.shared.util.MedidaNoPlural;
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
 * Opcao de substituicao para um item da refeicao.
 *
 * E o que da variedade ao plano sem tirar o paciente da prescricao: no lugar de
 * "2 fatias de pao integral", ele pode comer "1 tapioca media". O equivalente
 * nao entra na totalizacao — quem define os numeros do plano e o item
 * principal, e somar as alternativas contaria refeicoes que nao acontecem.
 */
@Entity
@Table(name = "equivalente_item",
        indexes = @Index(name = "ix_equivalente_item", columnList = "item_id"))
@Getter
@Setter
@NoArgsConstructor
public class EquivalenteItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_equivalente_item"))
    private ItemRefeicao item;

    @Column(name = "alimento_id")
    private Long alimentoId;

    @Column(name = "medida_id")
    private Long medidaId;

    @Column(nullable = false, length = 250)
    private String descricao;

    @Column(name = "descricao_medida", length = 120)
    private String descricaoMedida;

    @Column(precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Column(precision = 10, scale = 3)
    private BigDecimal gramas;

    public EquivalenteItem(String descricao) {
        this.descricao = descricao;
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
