package br.com.nutriplan.antropometria.domain;

import java.math.BigDecimal;

/**
 * Estado nutricional de criança e adolescente, pelo escore-z da OMS.
 *
 * As faixas mudam aos cinco anos e não é detalhe de tabela: até os cinco, um
 * escore-z acima de +1 é **risco de sobrepeso**, porque nessa idade a criança
 * ainda pode acompanhar a curva; dos cinco aos dezenove, o mesmo valor já é
 * sobrepeso. Classificar um adolescente pela faixa de criança subestimaria o
 * quadro em um grau inteiro.
 *
 * São as faixas adotadas pelo SISVAN, do Ministério da Saúde.
 */
public enum ClassificacaoInfantil {

    MAGREZA_ACENTUADA("Magreza acentuada"),
    MAGREZA("Magreza"),
    EUTROFIA("Eutrofia"),
    RISCO_DE_SOBREPESO("Risco de sobrepeso"),
    SOBREPESO("Sobrepeso"),
    OBESIDADE("Obesidade"),
    OBESIDADE_GRAVE("Obesidade grave"),

    MUITO_BAIXA_ESTATURA("Muito baixa estatura para a idade"),
    BAIXA_ESTATURA("Baixa estatura para a idade"),
    ESTATURA_ADEQUADA("Estatura adequada para a idade");

    private final String descricao;

    ClassificacaoInfantil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Aponta quadro que pede conduta, para a interface poder destacar. */
    public boolean exigeAtencao() {
        return this != EUTROFIA && this != ESTATURA_ADEQUADA;
    }

    /**
     * @param indicador qual curva produziu o escore
     * @param meses     idade em meses completos — decide a faixa de corte
     */
    public static ClassificacaoInfantil de(IndicadorDeCrescimento indicador,
                                           BigDecimal escoreZ, int meses) {
        if (escoreZ == null) {
            return null;
        }
        double z = escoreZ.doubleValue();

        if (indicador == IndicadorDeCrescimento.ESTATURA_PARA_IDADE) {
            if (z < -3) return MUITO_BAIXA_ESTATURA;
            if (z < -2) return BAIXA_ESTATURA;
            return ESTATURA_ADEQUADA;
        }

        if (z < -3) return MAGREZA_ACENTUADA;
        if (z < -2) return MAGREZA;
        if (z <= 1) return EUTROFIA;

        // A partir daqui a faixa depende da idade.
        if (meses <= 60) {
            if (z <= 2) return RISCO_DE_SOBREPESO;
            if (z <= 3) return SOBREPESO;
            return OBESIDADE;
        }
        if (z <= 2) return SOBREPESO;
        if (z <= 3) return OBESIDADE;
        return OBESIDADE_GRAVE;
    }
}
