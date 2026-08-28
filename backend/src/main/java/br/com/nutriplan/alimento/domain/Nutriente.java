package br.com.nutriplan.alimento.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catalogo dos nutrientes rastreados pelo sistema.
 *
 * Existe para que escalar, somar e exibir uma composicao sejam operacoes
 * genericas sobre esta lista, em vez de 30 linhas repetidas por operacao.
 * Antes disso a composicao era montada por um construtor posicional de dezenas
 * de argumentos, onde trocar dois nutrientes de lugar passaria despercebido —
 * um erro silencioso e grave num sistema que calcula prescricao.
 *
 * Acrescentar um nutriente novo passa a ser uma linha aqui, mais a coluna na
 * migration e o par getter/setter na entidade.
 *
 * @param grupo usado pela interface para agrupar a exibicao
 */
public record Nutriente(
        String chave,
        String rotulo,
        String unidade,
        Grupo grupo,
        Function<ComposicaoNutricional, BigDecimal> ler,
        BiConsumer<ComposicaoNutricional, BigDecimal> gravar
) {

    public enum Grupo { ENERGIA, MACRONUTRIENTE, LIPIDIO, MINERAL, VITAMINA, OUTRO }

    private static Nutriente n(String chave, String rotulo, String unidade, Grupo grupo,
                               Function<ComposicaoNutricional, BigDecimal> ler,
                               BiConsumer<ComposicaoNutricional, BigDecimal> gravar) {
        return new Nutriente(chave, rotulo, unidade, grupo, ler, gravar);
    }

    /** Ordem desta lista e a ordem de exibicao na interface e nos relatorios. */
    public static final List<Nutriente> TODOS = List.of(
            n("energiaKcal", "Energia", "kcal", Grupo.ENERGIA,
                    ComposicaoNutricional::getEnergiaKcal, ComposicaoNutricional::setEnergiaKcal),
            n("energiaKj", "Energia", "kJ", Grupo.ENERGIA,
                    ComposicaoNutricional::getEnergiaKj, ComposicaoNutricional::setEnergiaKj),

            n("proteinaG", "Proteínas", "g", Grupo.MACRONUTRIENTE,
                    ComposicaoNutricional::getProteinaG, ComposicaoNutricional::setProteinaG),
            n("carboidratoG", "Carboidratos", "g", Grupo.MACRONUTRIENTE,
                    ComposicaoNutricional::getCarboidratoG, ComposicaoNutricional::setCarboidratoG),
            n("acucaresG", "Açúcares totais", "g", Grupo.MACRONUTRIENTE,
                    ComposicaoNutricional::getAcucaresG, ComposicaoNutricional::setAcucaresG),
            n("acucaresAdicionadosG", "Açúcares adicionados", "g", Grupo.MACRONUTRIENTE,
                    ComposicaoNutricional::getAcucaresAdicionadosG, ComposicaoNutricional::setAcucaresAdicionadosG),
            n("fibraG", "Fibra alimentar", "g", Grupo.MACRONUTRIENTE,
                    ComposicaoNutricional::getFibraG, ComposicaoNutricional::setFibraG),

            n("lipideosG", "Gorduras totais", "g", Grupo.LIPIDIO,
                    ComposicaoNutricional::getLipideosG, ComposicaoNutricional::setLipideosG),
            n("gordurasSaturadasG", "Gorduras saturadas", "g", Grupo.LIPIDIO,
                    ComposicaoNutricional::getGordurasSaturadasG, ComposicaoNutricional::setGordurasSaturadasG),
            n("gordurasTransG", "Gorduras trans", "g", Grupo.LIPIDIO,
                    ComposicaoNutricional::getGordurasTransG, ComposicaoNutricional::setGordurasTransG),
            n("gordurasMonoinsaturadasG", "Gorduras monoinsaturadas", "g", Grupo.LIPIDIO,
                    ComposicaoNutricional::getGordurasMonoinsaturadasG, ComposicaoNutricional::setGordurasMonoinsaturadasG),
            n("gordurasPoliinsaturadasG", "Gorduras poli-insaturadas", "g", Grupo.LIPIDIO,
                    ComposicaoNutricional::getGordurasPoliinsaturadasG, ComposicaoNutricional::setGordurasPoliinsaturadasG),
            n("colesterolMg", "Colesterol", "mg", Grupo.LIPIDIO,
                    ComposicaoNutricional::getColesterolMg, ComposicaoNutricional::setColesterolMg),

            n("sodioMg", "Sódio", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getSodioMg, ComposicaoNutricional::setSodioMg),
            n("calcioMg", "Cálcio", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getCalcioMg, ComposicaoNutricional::setCalcioMg),
            n("ferroMg", "Ferro", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getFerroMg, ComposicaoNutricional::setFerroMg),
            n("magnesioMg", "Magnésio", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getMagnesioMg, ComposicaoNutricional::setMagnesioMg),
            n("fosforoMg", "Fósforo", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getFosforoMg, ComposicaoNutricional::setFosforoMg),
            n("potassioMg", "Potássio", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getPotassioMg, ComposicaoNutricional::setPotassioMg),
            n("zincoMg", "Zinco", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getZincoMg, ComposicaoNutricional::setZincoMg),
            n("cobreMg", "Cobre", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getCobreMg, ComposicaoNutricional::setCobreMg),
            n("manganesMg", "Manganês", "mg", Grupo.MINERAL,
                    ComposicaoNutricional::getManganesMg, ComposicaoNutricional::setManganesMg),
            n("selenioMcg", "Selênio", "mcg", Grupo.MINERAL,
                    ComposicaoNutricional::getSelenioMcg, ComposicaoNutricional::setSelenioMcg),

            n("vitaminaCMg", "Vitamina C", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getVitaminaCMg, ComposicaoNutricional::setVitaminaCMg),
            n("tiaminaMg", "Tiamina (B1)", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getTiaminaMg, ComposicaoNutricional::setTiaminaMg),
            n("riboflavinaMg", "Riboflavina (B2)", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getRiboflavinaMg, ComposicaoNutricional::setRiboflavinaMg),
            n("niacinaMg", "Niacina (B3)", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getNiacinaMg, ComposicaoNutricional::setNiacinaMg),
            n("piridoxinaMg", "Piridoxina (B6)", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getPiridoxinaMg, ComposicaoNutricional::setPiridoxinaMg),
            n("retinolMcg", "Retinol", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getRetinolMcg, ComposicaoNutricional::setRetinolMcg),
            n("reMcg", "Equivalente de retinol (RE)", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getReMcg, ComposicaoNutricional::setReMcg),
            n("raeMcg", "Equivalente de atividade de retinol (RAE)", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getRaeMcg, ComposicaoNutricional::setRaeMcg),
            n("vitaminaB12Mcg", "Cobalamina (B12)", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getVitaminaB12Mcg, ComposicaoNutricional::setVitaminaB12Mcg),
            n("folatoMcg", "Folato", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getFolatoMcg, ComposicaoNutricional::setFolatoMcg),
            n("vitaminaDMcg", "Vitamina D", "mcg", Grupo.VITAMINA,
                    ComposicaoNutricional::getVitaminaDMcg, ComposicaoNutricional::setVitaminaDMcg),
            n("vitaminaEMg", "Vitamina E", "mg", Grupo.VITAMINA,
                    ComposicaoNutricional::getVitaminaEMg, ComposicaoNutricional::setVitaminaEMg),

            n("umidadePct", "Umidade", "%", Grupo.OUTRO,
                    ComposicaoNutricional::getUmidadePct, ComposicaoNutricional::setUmidadePct),
            n("cinzasG", "Cinzas", "g", Grupo.OUTRO,
                    ComposicaoNutricional::getCinzasG, ComposicaoNutricional::setCinzasG)
    );

    public static final Map<String, Nutriente> POR_CHAVE =
            TODOS.stream().collect(Collectors.toMap(Nutriente::chave, x -> x));

    /**
     * Nutrientes cujo total nao pode ser simplesmente somado entre alimentos.
     * Umidade e um percentual e energia em kJ e redundante com a kcal; ambos
     * seguem sendo escalados por porcao, mas nao entram em totais de refeicao.
     */
    public static final List<Nutriente> SOMAVEIS = TODOS.stream()
            .filter(x -> !x.chave().equals("umidadePct"))
            .toList();
}
