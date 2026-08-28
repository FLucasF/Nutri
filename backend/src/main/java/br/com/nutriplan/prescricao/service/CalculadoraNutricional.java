package br.com.nutriplan.prescricao.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.ComposicaoNutricional;
import br.com.nutriplan.alimento.domain.Nutriente;
import br.com.nutriplan.prescricao.domain.ItemRefeicao;
import br.com.nutriplan.prescricao.domain.Refeicao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Totaliza a composicao nutricional de refeicoes e do plano.
 *
 * O ponto delicado deste calculo nao e a aritmetica, e a honestidade do
 * resultado. Tabelas de composicao trazem nutrientes nao determinados, e somar
 * "presente + ausente" tratando ausente como zero produziria um total que
 * parece exato e nao e — o profissional leria 4 mg de ferro num plano onde
 * metade dos itens sequer teve ferro analisado.
 *
 * A saida por isso nao e so um numero: cada nutriente vem acompanhado de
 * quantos itens contribuiram para ele. Quando ha lacuna, o total e um piso, e
 * a interface precisa dizer isso.
 */
@Component
public class CalculadoraNutricional {

    /** Percentual de energia por grama, usado na distribuicao de macronutrientes. */
    private static final BigDecimal KCAL_POR_G_PROTEINA = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_POR_G_CARBOIDRATO = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_POR_G_LIPIDEO = BigDecimal.valueOf(9);

    /**
     * Total de um conjunto de itens.
     *
     * @param itensNoCalculo   quantos itens tinham peso e alimento conhecidos
     * @param itensForaDoCalculo itens sem peso — qualitativos ou puramente textuais
     * @param cobertura        por nutriente, quantos itens tinham o dado
     */
    public record Total(
            ComposicaoNutricional composicao,
            int itensNoCalculo,
            int itensForaDoCalculo,
            Map<String, Integer> cobertura
    ) {
        /** Nutrientes que nenhum item informou. */
        public Set<String> nutrientesSemDado() {
            Set<String> ausentes = new TreeSet<>();
            for (Nutriente nutriente : Nutriente.TODOS) {
                if (cobertura.getOrDefault(nutriente.chave(), 0) == 0) {
                    ausentes.add(nutriente.chave());
                }
            }
            return ausentes;
        }

        /**
         * Nutrientes informados por parte dos itens, mas nao por todos: o total
         * existe, porem subestima o valor real.
         */
        public Set<String> nutrientesIncompletos() {
            Set<String> incompletos = new TreeSet<>();
            cobertura.forEach((chave, quantos) -> {
                if (quantos > 0 && quantos < itensNoCalculo) {
                    incompletos.add(chave);
                }
            });
            return incompletos;
        }

        public boolean confiavel() {
            return itensNoCalculo > 0 && nutrientesIncompletos().isEmpty();
        }
    }

    /**
     * Soma os itens usando a composicao dos alimentos fornecidos.
     *
     * @param alimentosPorId alimentos ja carregados, para evitar uma consulta por item
     */
    public Total totalizar(List<ItemRefeicao> itens, Map<Long, Alimento> alimentosPorId) {
        var acumulado = new ComposicaoNutricional();
        var cobertura = new java.util.HashMap<String, Integer>();
        int dentro = 0;
        int fora = 0;

        for (ItemRefeicao item : itens) {
            if (!item.entraNoCalculo()) {
                fora++;
                continue;
            }
            Alimento alimento = alimentosPorId.get(item.getAlimentoId());
            if (alimento == null) {
                // Alimento removido do acervo depois da prescricao. O item
                // permanece no plano, mas nao ha o que somar.
                fora++;
                continue;
            }

            ComposicaoNutricional daPorcao = alimento.composicaoPara(item.getGramas());
            acumulado = acumulado.somar(daPorcao);
            dentro++;

            for (Nutriente nutriente : Nutriente.TODOS) {
                if (nutriente.ler().apply(daPorcao) != null) {
                    cobertura.merge(nutriente.chave(), 1, Integer::sum);
                }
            }
        }

        return new Total(acumulado, dentro, fora, Map.copyOf(cobertura));
    }

    /** Total do plano inteiro, somando todas as refeicoes. */
    public Total totalizarRefeicoes(List<Refeicao> refeicoes, Map<Long, Alimento> alimentosPorId) {
        List<ItemRefeicao> todos = new ArrayList<>();
        refeicoes.forEach(r -> todos.addAll(r.getItens()));
        return totalizar(todos, alimentosPorId);
    }

    /**
     * Distribuicao percentual de energia entre os macronutrientes.
     *
     * Calculada a partir dos gramas de cada macro pelos fatores de Atwater, e
     * nao da energia declarada, para que os tres percentuais somem 100 mesmo
     * quando a fonte arredonda a energia de forma independente dos macros.
     *
     * Devolve nulo se faltar qualquer um dos tres: uma distribuicao com dois
     * macronutrientes nao e uma distribuicao, e apresenta-la induziria o
     * profissional a uma leitura errada.
     */
    public DistribuicaoMacros distribuicaoDeMacros(ComposicaoNutricional composicao) {
        BigDecimal proteina = composicao.getProteinaG();
        BigDecimal carboidrato = composicao.getCarboidratoG();
        BigDecimal lipideo = composicao.getLipideosG();

        if (proteina == null || carboidrato == null || lipideo == null) {
            return null;
        }

        BigDecimal kcalProteina = proteina.multiply(KCAL_POR_G_PROTEINA);
        BigDecimal kcalCarboidrato = carboidrato.multiply(KCAL_POR_G_CARBOIDRATO);
        BigDecimal kcalLipideo = lipideo.multiply(KCAL_POR_G_LIPIDEO);
        BigDecimal totalKcal = kcalProteina.add(kcalCarboidrato).add(kcalLipideo);

        if (totalKcal.signum() <= 0) {
            return null;
        }

        return new DistribuicaoMacros(
                percentual(kcalProteina, totalKcal),
                percentual(kcalCarboidrato, totalKcal),
                percentual(kcalLipideo, totalKcal),
                totalKcal.setScale(1, RoundingMode.HALF_UP));
    }

    public record DistribuicaoMacros(
            BigDecimal proteinaPct,
            BigDecimal carboidratoPct,
            BigDecimal lipideoPct,
            BigDecimal energiaCalculadaKcal
    ) {}

    private BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }

    /**
     * Adequacao do prescrito frente a meta energetica, em percentual.
     * Nulo quando nao ha meta definida ou energia calculada.
     */
    public BigDecimal adequacaoEnergetica(ComposicaoNutricional composicao, BigDecimal metaKcal) {
        BigDecimal energia = composicao.getEnergiaKcal();
        if (energia == null || metaKcal == null || metaKcal.signum() <= 0) {
            return null;
        }
        return energia.multiply(BigDecimal.valueOf(100))
                .divide(metaKcal, 1, RoundingMode.HALF_UP);
    }
}
