package br.com.nutriplan.exame.domain;

import java.math.BigDecimal;

/**
 * Posicao do resultado frente a faixa de referencia.
 *
 * Decidida uma vez, na entrada, e gravada com o exame. Recalcular na leitura
 * faria um resultado antigo mudar de classificacao quando a faixa cadastrada
 * fosse corrigida — o passado nao muda porque o laboratorio trocou de metodo.
 */
public enum ClassificacaoDoExame {

    ABAIXO("Abaixo da referência"),
    NORMAL("Dentro da referência"),
    ACIMA("Acima da referência");

    private final String descricao;

    ClassificacaoDoExame(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Classifica o valor contra a faixa.
     *
     * @return nulo quando nao ha valor ou nao ha faixa — e nulo aqui significa
     *         "não classificado", que e diferente de "normal"
     */
    public static ClassificacaoDoExame de(BigDecimal valor, BigDecimal minimo, BigDecimal maximo) {
        if (valor == null || (minimo == null && maximo == null)) {
            return null;
        }
        if (minimo != null && valor.compareTo(minimo) < 0) {
            return ABAIXO;
        }
        if (maximo != null && valor.compareTo(maximo) > 0) {
            return ACIMA;
        }
        return NORMAL;
    }
}
