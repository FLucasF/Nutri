package br.com.nutriplan.antropometria.domain;

import br.com.nutriplan.paciente.domain.Sexo;
import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Um ponto da curva de crescimento da OMS: os parametros LMS de um indicador,
 * para um sexo, numa idade em meses.
 *
 * Guardar L, M e S — e nao a tabela de pontos de corte — e o que permite
 * calcular o escore-z exato em vez de so dizer em que faixa o valor caiu.
 */
@Entity
@Table(name = "curva_crescimento",
        uniqueConstraints = @UniqueConstraint(name = "uk_curva",
                columnNames = {"indicador", "sexo", "mes"}),
        indexes = @Index(name = "ix_curva_busca", columnList = "indicador, sexo, mes"))
@Getter
@Setter
@NoArgsConstructor
public class CurvaDeCrescimento extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IndicadorDeCrescimento indicador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Sexo sexo;

    @Column(nullable = false)
    private Integer mes;

    /** Assimetria da distribuicao (Box-Cox). */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal l;

    /** Mediana do indicador nessa idade. */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal m;

    /** Coeficiente de variacao. */
    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal s;

    public CurvaDeCrescimento(IndicadorDeCrescimento indicador, Sexo sexo, Integer mes,
                              BigDecimal l, BigDecimal m, BigDecimal s) {
        this.indicador = indicador;
        this.sexo = sexo;
        this.mes = mes;
        this.l = l;
        this.m = m;
        this.s = s;
    }

    /**
     * Referencia da OMS a que este ponto pertence.
     *
     * Ate 60 meses valem os padroes de 2006, construidos a partir de criancas
     * em condicoes ideais de crescimento; acima, a referencia de 2007, que e
     * uma reconstrucao da referencia do NCHS. Sao trabalhos diferentes, e a
     * distincao precisa aparecer no resultado.
     */
    public String referencia() {
        return mes <= 60 ? "OMS 2006 (0 a 5 anos)" : "OMS 2007 (5 a 19 anos)";
    }
}
