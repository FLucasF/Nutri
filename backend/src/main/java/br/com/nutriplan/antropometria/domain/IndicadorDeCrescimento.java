package br.com.nutriplan.antropometria.domain;

/**
 * Indicador antropometrico infantil, lido contra as curvas da OMS.
 *
 * Cada um responde a uma pergunta diferente, e por isso os dois existem: o IMC
 * para idade diz como esta o peso para a altura que a crianca tem agora; a
 * estatura para idade diz se ela cresceu o que deveria — e baixa estatura e
 * consequencia de deficit prolongado, que o IMC nao mostra.
 */
public enum IndicadorDeCrescimento {

    IMC_PARA_IDADE("IMC para idade"),
    ESTATURA_PARA_IDADE("Estatura para idade");

    private final String descricao;

    IndicadorDeCrescimento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * A correcao da OMS para escore-z extremo vale para indicador baseado em
     * peso, e nao para estatura.
     *
     * Em indicadores de peso a distribuicao tem cauda longa, e a formula LMS
     * produz valores absurdos alem de tres desvios. A OMS prescreve, nesses
     * casos, extrapolar linearmente a partir do intervalo entre o segundo e o
     * terceiro desvio. Estatura nao tem esse problema: a distribuicao e
     * proxima da normal, e a formula vale em toda a faixa.
     */
    public boolean exigeCorrecaoDeCaudas() {
        return this == IMC_PARA_IDADE;
    }
}
