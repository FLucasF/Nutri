package br.com.nutriplan.labtest.domain;

import java.util.ArrayList;
import java.util.List;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Um conjunto nomeado de parâmetros — o botão que preenche o pedido.
 *
 * "Ao clicarmos em algum desses botões, será preenchido na direita os
 * marcadores ligados ao botão." São os 25 painéis das páginas 6 a 13.
 *
 * Conta nula identifica painel do sistema, comum a todos. Conta preenchida é
 * painel do consultório — o que ele descreve como "caso ele queira selecionar
 * alguns e formular um seu, mais genérico para primeiras consultas". É esse
 * que a tela marca com uma TAG na listagem.
 */
@Entity
@Table(name = "labtest_panel",
        indexes = @Index(name = "ix_labtest_panel_account", columnList = "account_id"))
@Getter
@Setter
@NoArgsConstructor
public class LabtestPanel extends BaseEntity {

    @Column(name = "account_id")
    private Long accountId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer order = 0;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "panel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order")
    private List<LabtestPanelParameter> parameters = new ArrayList<>();

    public LabtestPanel(Long accountId, String name, Integer order) {
        this.accountId = accountId;
        this.name = name;
        this.order = order;
    }

    /** Um painel do sistema serve de ponto de partida e ninguém o edita. */
    public boolean isSystemPanel() {
        return accountId == null;
    }

    public void add(LabtestPanelParameter parameter) {
        parameter.setPanel(this);
        parameters.add(parameter);
    }
}
