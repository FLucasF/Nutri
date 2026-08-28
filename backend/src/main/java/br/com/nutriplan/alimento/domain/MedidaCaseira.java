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
 * Porcao usual de um alimento — "colher de sopa cheia", "fatia media", "unidade".
 *
 * E o que torna a prescricao utilizavel pelo paciente: ninguem pesa 23 g de
 * arroz, mas todo mundo entende "2 colheres de sopa". O peso em gramas e o que
 * o calculo usa; a descricao e o que aparece no plano impresso.
 */
@Entity
@Table(name = "medida_caseira", indexes = @Index(name = "ix_medida_alimento", columnList = "alimento_id"))
@Getter
@Setter
@NoArgsConstructor
public class MedidaCaseira extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alimento_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_medida_alimento"))
    private Alimento alimento;

    /**
     * Consultorio dono da porcao. Nulo identifica porcao do acervo base,
     * compartilhada por todos; preenchido, so o consultorio dono a enxerga.
     */
    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(name = "gramas", nullable = false, precision = 10, scale = 3)
    private BigDecimal gramas;

    /** Marca a porcao mostrada por padrao ao adicionar o alimento ao plano. */
    @Column(name = "padrao", nullable = false)
    private boolean padrao = false;

    public MedidaCaseira(String descricao, BigDecimal gramas) {
        this.descricao = descricao;
        this.gramas = gramas;
    }

    /** Peso total de uma quantidade desta medida (ex.: 2,5 colheres). */
    public BigDecimal gramasPara(BigDecimal quantidade) {
        return gramas.multiply(quantidade);
    }

    public boolean ehDoAcervoBase() {
        return contaId == null;
    }

    /** Porcao do acervo base nao pode ser alterada por nenhum consultorio. */
    public boolean editavelPor(Long conta) {
        return contaId != null && contaId.equals(conta);
    }
}
