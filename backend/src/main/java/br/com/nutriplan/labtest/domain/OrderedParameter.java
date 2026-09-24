package br.com.nutriplan.labtest.domain;

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

@Entity
@Table(name = "ordered_parameter",
        indexes = @Index(name = "ix_ordered_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrderedParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_solicitado_solicitacao"))
    private LabtestOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_solicitado_parametro"))
    private LabtestParameter parameter;

    /** De que painel o exame veio. Nulo quando foi marcado a mão. */
    @Column(name = "panel_name", length = 120)
    private String panelName;

    /**
     * Desligado, o exame fica no pedido e sai do PDF. É o "inativo" do
     * cliente: tirar sem apagar, para religar sem refazer o pedido.
     */
    @Column(nullable = false)
    private boolean active = true;

    public OrderedParameter(LabtestOrder order, LabtestParameter parameter) {
        this.order = order;
        this.parameter = parameter;
    }

    public OrderedParameter(LabtestOrder order, LabtestParameter parameter,
                            String panelName, boolean active) {
        this(order, parameter);
        this.panelName = panelName;
        this.active = active;
    }
}
