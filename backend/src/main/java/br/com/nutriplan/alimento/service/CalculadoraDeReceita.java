package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.ComposicaoNutricional;
import br.com.nutriplan.alimento.domain.IngredienteReceita;
import br.com.nutriplan.alimento.domain.Nutriente;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcula a composicao de uma preparacao a partir dos ingredientes.
 *
 * A conta em si e simples — somar o que cada ingrediente contribui e dividir
 * pelo peso final. O que exige cuidado sao duas coisas que a soma esconde.
 *
 * <p><b>O peso final nao e a soma dos ingredientes.</b> Cozinhar perde ou ganha
 * agua: 100 g de arroz cru viram cerca de 250 g cozido, e 100 g de carne viram
 * cerca de 70 g grelhada. Dividir pela soma dos ingredientes crus produziria
 * uma composicao por 100 g errada por um fator grande — no caso do arroz, duas
 * vezes e meia mais concentrada do que a realidade. Por isso o rendimento e
 * campo do nutricionista, e quando ele nao informa, o resultado diz que a soma
 * foi usada como estimativa em vez de apresentar o numero como medido.
 *
 * <p><b>Um nutriente ausente em parte dos ingredientes vira piso, nao total.</b>
 * Se a farinha tem fibra declarada e o fermento nao, a fibra somada e no minimo
 * aquilo — pode ser mais. Somar tratando o ausente como zero produziria um
 * numero que aparenta exatidao. Por isso o resultado carrega a lista de
 * nutrientes incompletos, e nutriente que nenhum ingrediente determinou
 * permanece nulo.
 */
@Component
public class CalculadoraDeReceita {

    /**
     * @param composicao          composicao por 100 g da preparacao pronta
     * @param pesoDosIngredientes soma do peso dos ingredientes
     * @param rendimentoUsado     peso final usado no calculo
     * @param rendimentoEstimado  true quando o rendimento nao foi informado e a
     *                            soma dos ingredientes foi usada em seu lugar
     * @param nutrientesIncompletos nutrientes somados a partir de apenas parte
     *                              dos ingredientes — o valor e um piso
     */
    public record Resultado(
            ComposicaoNutricional composicao,
            BigDecimal pesoDosIngredientes,
            BigDecimal rendimentoUsado,
            boolean rendimentoEstimado,
            Set<String> nutrientesIncompletos
    ) {}

    public Resultado calcular(List<IngredienteReceita> ingredientes, BigDecimal rendimentoInformado) {
        BigDecimal pesoDosIngredientes = BigDecimal.ZERO;
        var total = new ComposicaoNutricional();
        Set<String> presentesEmAlgum = new LinkedHashSet<>();
        Set<String> ausentesEmAlgum = new LinkedHashSet<>();

        for (IngredienteReceita ingrediente : ingredientes) {
            Alimento alimento = ingrediente.getAlimento();
            BigDecimal gramas = ingrediente.getGramas();
            pesoDosIngredientes = pesoDosIngredientes.add(gramas);

            ComposicaoNutricional contribuicao = alimento.getComposicao().paraGramas(gramas);
            total = total.somar(contribuicao);

            for (Nutriente nutriente : Nutriente.SOMAVEIS) {
                if (nutriente.ler().apply(contribuicao) != null) {
                    presentesEmAlgum.add(nutriente.chave());
                } else {
                    ausentesEmAlgum.add(nutriente.chave());
                }
            }
        }

        boolean estimado = rendimentoInformado == null;
        BigDecimal rendimento = estimado ? pesoDosIngredientes : rendimentoInformado;

        // Receita sem ingrediente: nao ha o que calcular, e dividir por zero
        // seria a unica forma de errar aqui.
        if (rendimento.signum() <= 0) {
            return new Resultado(new ComposicaoNutricional(), BigDecimal.ZERO,
                    BigDecimal.ZERO, estimado, Set.of());
        }

        // A composicao de um alimento e sempre por 100 g. O total acumulado
        // corresponde ao rendimento inteiro, entao volta para a base de 100.
        BigDecimal fator = BigDecimal.valueOf(100)
                .divide(rendimento, 10, RoundingMode.HALF_UP);
        var por100g = new ComposicaoNutricional();
        for (Nutriente nutriente : Nutriente.TODOS) {
            BigDecimal valor = nutriente.ler().apply(total);
            if (valor != null) {
                nutriente.gravar().accept(por100g, valor.multiply(fator)
                        .setScale(ComposicaoNutricional.ESCALA, RoundingMode.HALF_UP));
            }
        }

        // Incompleto e o que apareceu em alguns ingredientes e faltou noutros.
        // O que faltou em todos nem entra: continua nulo, e nulo ja diz "nao
        // determinado" sem precisar de aviso.
        Set<String> incompletos = new LinkedHashSet<>(presentesEmAlgum);
        incompletos.retainAll(ausentesEmAlgum);

        return new Resultado(por100g, pesoDosIngredientes, rendimento, estimado, incompletos);
    }
}
