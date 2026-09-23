package br.com.nutriplan.labtest.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Um parâmetro dentro de um painel, na ordem em que ele entra no pedido. */
@Entity
@Table(name = "labtest_panel_parameter",
        indexes = @Index(name = "ix_panel_parameter_panel", columnList = "panel_id"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_panel_parameter", columnNames = {"panel_id", "parameter_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LabtestPanelParameter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "panel_id", nullable = false)
    private LabtestPanel panel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parameter_id", nullable = false)
    private LabtestParameter parameter;

    @Column(name = "sort_order", nullable = false)
    private Integer order = 0;

    public LabtestPanelParameter(LabtestParameter parameter, Integer order) {
        this.parameter = parameter;
        this.order = order;
    }
}
