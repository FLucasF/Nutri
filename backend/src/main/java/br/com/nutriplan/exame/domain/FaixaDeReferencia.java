package br.com.nutriplan.exame.domain;

import br.com.nutriplan.paciente.domain.Sexo;
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

import java.math.BigDecimal;

/**
 * Faixa de referencia de um parametro, por sexo e faixa etaria.
 *
 * Extremo nulo significa "sem limite desse lado": LDL nao tem minimo de
 * preocupacao, e o valor de referencia e "abaixo de 130".
 */
@Entity
@Table(name = "faixa_referencia",
        indexes = @Index(name = "ix_faixa_parametro", columnList = "parametro_id"))
@Getter
@Setter
@NoArgsConstructor
public class FaixaDeReferencia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parametro_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_faixa_parametro"))
    private ParametroExame parametro;

    /** Nulo vale para os dois sexos. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Sexo sexo;

    @Column(name = "idade_min")
    private Integer idadeMin;

    @Column(name = "idade_max")
    private Integer idadeMax;

    @Column(precision = 12, scale = 3)
    private BigDecimal minimo;

    @Column(precision = 12, scale = 3)
    private BigDecimal maximo;

    /** A faixa serve ao paciente descrito? Sexo nulo e idade nula nao restringem. */
    public boolean serve(Sexo sexoDoPaciente, Integer idade) {
        if (sexo != null && sexo != sexoDoPaciente) {
            return false;
        }
        if (idadeMin != null && (idade == null || idade < idadeMin)) {
            return false;
        }
        return idadeMax == null || (idade != null && idade <= idadeMax);
    }

    /** Faixa mais especifica ganha: a que restringe sexo vale mais que a geral. */
    public int especificidade() {
        int pontos = 0;
        if (sexo != null) pontos++;
        if (idadeMin != null || idadeMax != null) pontos++;
        return pontos;
    }

    public String comoTexto() {
        if (minimo != null && maximo != null) {
            return "%s a %s".formatted(limpo(minimo), limpo(maximo));
        }
        if (maximo != null) {
            return "até %s".formatted(limpo(maximo));
        }
        return "a partir de %s".formatted(limpo(minimo));
    }

    private String limpo(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace(".", ",");
    }
}
