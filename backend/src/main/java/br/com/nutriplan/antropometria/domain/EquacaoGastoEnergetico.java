package br.com.nutriplan.antropometria.domain;

import br.com.nutriplan.paciente.domain.Sexo;

/**
 * Equações preditivas de gasto energético basal.
 *
 * A equação usada fica gravada junto do resultado. Sem isso, uma avaliação
 * antiga mudaria de valor caso o sistema passasse a adotar outra equação —
 * e o histórico do paciente deixaria de ser comparável consigo mesmo.
 *
 * Todas dependem de peso, altura, sexo e idade.
 */
public enum EquacaoGastoEnergetico {

    /** Mifflin-St Jeor. Boa acurácia na população adulta geral. */
    MIFFLIN_ST_JEOR("Mifflin-St Jeor") {
        @Override
        public double basal(double pesoKg, double alturaCm, Sexo sexo, int idade) {
            double comum = 10 * pesoKg + 6.25 * alturaCm - 5 * idade;
            return sexo == Sexo.MASCULINO ? comum + 5 : comum - 161;
        }
    },

    /** Harris-Benedict, na forma revisada. */
    HARRIS_BENEDICT("Harris-Benedict revisada") {
        @Override
        public double basal(double pesoKg, double alturaCm, Sexo sexo, int idade) {
            return sexo == Sexo.MASCULINO
                    ? 88.362 + 13.397 * pesoKg + 4.799 * alturaCm - 5.677 * idade
                    : 447.593 + 9.247 * pesoKg + 3.098 * alturaCm - 4.330 * idade;
        }
    };

    private final String descricao;

    EquacaoGastoEnergetico(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Gasto energético basal, em kcal por dia. */
    public abstract double basal(double pesoKg, double alturaCm, Sexo sexo, int idade);
}
