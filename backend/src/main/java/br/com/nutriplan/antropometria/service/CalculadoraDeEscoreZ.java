package br.com.nutriplan.antropometria.service;

import br.com.nutriplan.antropometria.domain.CurvaDeCrescimento;
import br.com.nutriplan.antropometria.domain.IndicadorDeCrescimento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escore-z pelo método LMS da OMS.
 *
 * A distribuição de um indicador antropométrico não é normal — ela é assimétrica,
 * e a assimetria muda com a idade. O método LMS descreve isso com três números
 * por idade: L (a transformação que normaliza), M (a mediana) e S (o
 * coeficiente de variação). Com eles o escore-z sai por fórmula, e não por
 * consulta a uma tabela de pontos de corte:
 *
 * <pre>
 *   z = ((valor / M)^L − 1) / (L × S)      quando L ≠ 0
 *   z = ln(valor / M) / S                  quando L = 0
 * </pre>
 *
 * A vantagem prática é dizer "escore-z −2,3" em vez de "entre −3 e −2".
 */
@Component
public class CalculadoraDeEscoreZ {

    /** Escala do resultado: dois decimais é como o escore-z se lê na clínica. */
    private static final int ESCALA = 2;

    /**
     * @return o escore-z, ou nulo quando o valor não permite cálculo
     */
    public BigDecimal calcular(CurvaDeCrescimento curva, BigDecimal valor) {
        if (curva == null || valor == null || valor.signum() <= 0) {
            return null;
        }
        double l = curva.getL().doubleValue();
        double m = curva.getM().doubleValue();
        double s = curva.getS().doubleValue();
        double y = valor.doubleValue();

        double z = bruto(y, l, m, s);

        // A correção da OMS para as caudas.
        //
        // Além de três desvios, a fórmula LMS produz valores absurdos em
        // indicadores baseados em peso: a cauda da distribuição é longa, e a
        // transformação de Box-Cox exagera o que está fora dela. A OMS
        // prescreve, nesses casos, extrapolar linearmente usando a distância
        // entre o segundo e o terceiro desvio como unidade.
        if (curva.getIndicador().exigeCorrecaoDeCaudas()) {
            if (z > 3) {
                double sd3 = valorNoDesvio(3, l, m, s);
                double sd2 = valorNoDesvio(2, l, m, s);
                z = 3 + (y - sd3) / (sd3 - sd2);
            } else if (z < -3) {
                double sd3 = valorNoDesvio(-3, l, m, s);
                double sd2 = valorNoDesvio(-2, l, m, s);
                z = -3 + (y - sd3) / (sd2 - sd3);
            }
        }

        if (Double.isNaN(z) || Double.isInfinite(z)) {
            return null;
        }
        return BigDecimal.valueOf(z).setScale(ESCALA, RoundingMode.HALF_UP);
    }

    private double bruto(double y, double l, double m, double s) {
        if (l == 0) {
            return Math.log(y / m) / s;
        }
        return (Math.pow(y / m, l) - 1) / (l * s);
    }

    /** O valor do indicador no desvio-padrão pedido — a fórmula LMS invertida. */
    private double valorNoDesvio(int desvios, double l, double m, double s) {
        if (l == 0) {
            return m * Math.exp(s * desvios);
        }
        return m * Math.pow(1 + l * s * desvios, 1 / l);
    }

    /**
     * Qual curva serve a esta idade.
     *
     * Devolve o indicador e a idade em meses já limitada ao alcance das
     * tabelas. Acima de 228 meses — dezenove anos — não há curva: a partir daí
     * vale a classificação de adulto.
     */
    public static boolean idadeTemCurva(Integer meses) {
        return meses != null && meses >= 0 && meses <= 228;
    }

    /** Indicadores que se calculam a partir de peso e altura. */
    public static IndicadorDeCrescimento[] indicadoresDisponiveis() {
        return IndicadorDeCrescimento.values();
    }
}
