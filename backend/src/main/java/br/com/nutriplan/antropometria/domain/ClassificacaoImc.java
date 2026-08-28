package br.com.nutriplan.antropometria.domain;

import java.math.BigDecimal;

/**
 * Faixas de índice de massa corporal para adultos, segundo a Organização
 * Mundial da Saúde.
 *
 * A classificação só se aplica a adultos. Para crianças e adolescentes a
 * leitura correta é por percentil de idade e sexo, e aplicar a faixa adulta
 * produziria conclusão errada — por isso {@link #paraAdulto} devolve nulo
 * abaixo de 20 anos, e cabe a quem chama explicar o motivo.
 */
public enum ClassificacaoImc {

    BAIXO_PESO("Baixo peso", null, 18.5),
    EUTROFIA("Eutrofia", 18.5, 25.0),
    SOBREPESO("Sobrepeso", 25.0, 30.0),
    OBESIDADE_I("Obesidade grau I", 30.0, 35.0),
    OBESIDADE_II("Obesidade grau II", 35.0, 40.0),
    OBESIDADE_III("Obesidade grau III", 40.0, null);

    /** Abaixo desta idade a faixa adulta não se aplica. */
    public static final int IDADE_MINIMA_ADULTO = 20;

    private final String descricao;
    private final Double limiteInferior;
    private final Double limiteSuperior;

    ClassificacaoImc(String descricao, Double limiteInferior, Double limiteSuperior) {
        this.descricao = descricao;
        this.limiteInferior = limiteInferior;
        this.limiteSuperior = limiteSuperior;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Classifica o IMC de um adulto.
     *
     * @return nulo se o IMC não foi calculado, ou se a idade é conhecida e
     *         inferior a {@value #IDADE_MINIMA_ADULTO} anos
     */
    public static ClassificacaoImc paraAdulto(BigDecimal imc, Integer idade) {
        if (imc == null) {
            return null;
        }
        if (idade != null && idade < IDADE_MINIMA_ADULTO) {
            return null;
        }
        double valor = imc.doubleValue();
        for (ClassificacaoImc faixa : values()) {
            boolean acimaDoPiso = faixa.limiteInferior == null || valor >= faixa.limiteInferior;
            boolean abaixoDoTeto = faixa.limiteSuperior == null || valor < faixa.limiteSuperior;
            if (acimaDoPiso && abaixoDoTeto) {
                return faixa;
            }
        }
        return null;
    }
}
