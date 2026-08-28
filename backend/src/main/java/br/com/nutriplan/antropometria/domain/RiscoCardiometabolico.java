package br.com.nutriplan.antropometria.domain;

import br.com.nutriplan.paciente.domain.Sexo;

import java.math.BigDecimal;

/**
 * Risco associado à distribuição de gordura corporal, lido pela relação
 * cintura-quadril.
 *
 * Os pontos de corte são específicos por sexo. Sem sexo informado o sistema
 * calcula a relação mas não classifica: aplicar o corte masculino a uma
 * paciente, ou o inverso, produziria orientação clínica errada.
 */
public enum RiscoCardiometabolico {

    BAIXO("Baixo"),
    MODERADO("Moderado"),
    ALTO("Alto");

    private final String descricao;

    RiscoCardiometabolico(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Classifica pela relação cintura-quadril.
     *
     * @return nulo quando a relação não pôde ser calculada ou o sexo é desconhecido
     */
    public static RiscoCardiometabolico porRelacaoCinturaQuadril(BigDecimal relacao, Sexo sexo) {
        if (relacao == null || sexo == null) {
            return null;
        }
        double valor = relacao.doubleValue();
        return switch (sexo) {
            case FEMININO -> valor < 0.80 ? BAIXO : valor < 0.85 ? MODERADO : ALTO;
            case MASCULINO -> valor < 0.90 ? BAIXO : valor < 1.00 ? MODERADO : ALTO;
        };
    }
}
