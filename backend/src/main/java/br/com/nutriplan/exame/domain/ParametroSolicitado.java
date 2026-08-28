package br.com.nutriplan.exame.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
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
@Table(name = "parametro_solicitado",
        indexes = @Index(name = "ix_solicitado_solicitacao", columnList = "solicitacao_id"))
@Getter
@Setter
@NoArgsConstructor
public class ParametroSolicitado extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitacao_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_solicitado_solicitacao"))
    private SolicitacaoDeExame solicitacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parametro_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_solicitado_parametro"))
    private ParametroExame parametro;

    public ParametroSolicitado(SolicitacaoDeExame solicitacao, ParametroExame parametro) {
        this.solicitacao = solicitacao;
        this.parametro = parametro;
    }
}
