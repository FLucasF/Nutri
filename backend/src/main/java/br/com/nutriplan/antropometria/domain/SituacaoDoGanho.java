package br.com.nutriplan.antropometria.domain;

import java.math.BigDecimal;

/** Posicao do ganho de peso da gestante frente a faixa esperada para a semana. */
public enum SituacaoDoGanho {

    ABAIXO("Abaixo do esperado"),
    ADEQUADO("Dentro do esperado"),
    ACIMA("Acima do esperado");

    private final String descricao;

    SituacaoDoGanho(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static SituacaoDoGanho de(BigDecimal ganho, BigDecimal minimo, BigDecimal maximo) {
        if (ganho == null || minimo == null || maximo == null) {
            return null;
        }
        if (ganho.compareTo(minimo) < 0) {
            return ABAIXO;
        }
        if (ganho.compareTo(maximo) > 0) {
            return ACIMA;
        }
        return ADEQUADO;
    }
}
