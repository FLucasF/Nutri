package br.com.nutriplan.alimento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composicao nutricional sempre referida a 100 g de parte comestivel — a mesma
 * base usada pela TACO e pelos rotulos brasileiros.
 *
 * Campos nulos significam nutriente nao determinado na fonte. Nulo e ausencia
 * de informacao, nao zero: num software clinico exibir "não informado" e
 * correto, exibir 0 mg de sodio para um alimento nao analisado e falso.
 *
 * Os valores usam BigDecimal porque um plano alimentar soma centenas de
 * parcelas, e o erro acumulado de ponto flutuante apareceria no total.
 *
 * As operacoes de escala e soma percorrem o catalogo em {@link Nutriente},
 * de modo que incluir um nutriente novo nao exige tocar nesta logica.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ComposicaoNutricional {

    /** Escala de arredondamento dos resultados de calculo. */
    public static final int ESCALA = 3;

    // --- energia -----------------------------------------------------------
    @Column(name = "energia_kcal",   precision = 10, scale = 4) private BigDecimal energiaKcal;
    @Column(name = "energia_kj",     precision = 10, scale = 4) private BigDecimal energiaKj;

    // --- macronutrientes ---------------------------------------------------
    @Column(name = "proteina_g",              precision = 10, scale = 4) private BigDecimal proteinaG;
    @Column(name = "carboidrato_g",           precision = 10, scale = 4) private BigDecimal carboidratoG;
    @Column(name = "acucares_g",              precision = 10, scale = 4) private BigDecimal acucaresG;
    @Column(name = "acucares_adicionados_g",  precision = 10, scale = 4) private BigDecimal acucaresAdicionadosG;
    @Column(name = "fibra_g",                 precision = 10, scale = 4) private BigDecimal fibraG;

    // --- lipidios ----------------------------------------------------------
    @Column(name = "lipideos_g",                  precision = 10, scale = 4) private BigDecimal lipideosG;
    @Column(name = "gorduras_saturadas_g",        precision = 10, scale = 4) private BigDecimal gordurasSaturadasG;
    @Column(name = "gorduras_trans_g",            precision = 10, scale = 4) private BigDecimal gordurasTransG;
    @Column(name = "gorduras_monoinsaturadas_g",  precision = 10, scale = 4) private BigDecimal gordurasMonoinsaturadasG;
    @Column(name = "gorduras_poliinsaturadas_g",  precision = 10, scale = 4) private BigDecimal gordurasPoliinsaturadasG;
    @Column(name = "colesterol_mg",               precision = 10, scale = 4) private BigDecimal colesterolMg;

    // --- minerais ----------------------------------------------------------
    @Column(name = "sodio_mg",    precision = 10, scale = 4) private BigDecimal sodioMg;
    @Column(name = "calcio_mg",   precision = 10, scale = 4) private BigDecimal calcioMg;
    @Column(name = "ferro_mg",    precision = 10, scale = 4) private BigDecimal ferroMg;
    @Column(name = "magnesio_mg", precision = 10, scale = 4) private BigDecimal magnesioMg;
    @Column(name = "fosforo_mg",  precision = 10, scale = 4) private BigDecimal fosforoMg;
    @Column(name = "potassio_mg", precision = 10, scale = 4) private BigDecimal potassioMg;
    @Column(name = "zinco_mg",    precision = 10, scale = 4) private BigDecimal zincoMg;
    @Column(name = "cobre_mg",    precision = 10, scale = 4) private BigDecimal cobreMg;
    @Column(name = "manganes_mg", precision = 10, scale = 4) private BigDecimal manganesMg;
    @Column(name = "selenio_mcg", precision = 10, scale = 4) private BigDecimal selenioMcg;

    // --- vitaminas ---------------------------------------------------------
    @Column(name = "vitamina_c_mg",  precision = 10, scale = 4) private BigDecimal vitaminaCMg;
    @Column(name = "tiamina_mg",     precision = 10, scale = 4) private BigDecimal tiaminaMg;
    @Column(name = "riboflavina_mg", precision = 10, scale = 4) private BigDecimal riboflavinaMg;
    @Column(name = "niacina_mg",     precision = 10, scale = 4) private BigDecimal niacinaMg;
    @Column(name = "piridoxina_mg",  precision = 10, scale = 4) private BigDecimal piridoxinaMg;
    @Column(name = "retinol_mcg",    precision = 10, scale = 4) private BigDecimal retinolMcg;
    @Column(name = "re_mcg",         precision = 10, scale = 4) private BigDecimal reMcg;
    @Column(name = "rae_mcg",        precision = 10, scale = 4) private BigDecimal raeMcg;
    @Column(name = "vitamina_b12_mcg", precision = 10, scale = 4) private BigDecimal vitaminaB12Mcg;
    @Column(name = "folato_mcg",       precision = 10, scale = 4) private BigDecimal folatoMcg;
    @Column(name = "vitamina_d_mcg",   precision = 10, scale = 4) private BigDecimal vitaminaDMcg;
    @Column(name = "vitamina_e_mg",    precision = 10, scale = 4) private BigDecimal vitaminaEMg;

    // --- outros ------------------------------------------------------------
    @Column(name = "umidade_pct", precision = 10, scale = 4) private BigDecimal umidadePct;
    @Column(name = "cinzas_g",    precision = 10, scale = 4) private BigDecimal cinzasG;

    /**
     * Escala esta composicao (base 100 g) para a quantidade informada em gramas.
     * Nutrientes ausentes na fonte permanecem ausentes: nao ha como inferi-los.
     */
    public ComposicaoNutricional paraGramas(BigDecimal gramas) {
        BigDecimal fator = gramas.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        var resultado = new ComposicaoNutricional();
        for (Nutriente nutriente : Nutriente.TODOS) {
            BigDecimal valor = nutriente.ler().apply(this);
            if (valor != null) {
                nutriente.gravar().accept(resultado,
                        valor.multiply(fator).setScale(ESCALA, RoundingMode.HALF_UP));
            }
        }
        return resultado;
    }

    /**
     * Soma outra composicao a esta, devolvendo uma nova instancia.
     *
     * Ausente somado a presente devolve o presente: ao totalizar uma refeicao,
     * um alimento sem dado de zinco nao pode zerar o zinco dos demais. O efeito
     * colateral e que o total vira um piso, nao um valor exato — por isso o
     * calculo de refeicao reporta a parte, em {@link #nutrientesAusentes()},
     * quais nutrientes ficaram incompletos.
     */
    public ComposicaoNutricional somar(ComposicaoNutricional outra) {
        if (outra == null) {
            return this;
        }
        var resultado = new ComposicaoNutricional();
        for (Nutriente nutriente : Nutriente.SOMAVEIS) {
            BigDecimal a = nutriente.ler().apply(this);
            BigDecimal b = nutriente.ler().apply(outra);
            BigDecimal soma;
            if (a == null) {
                soma = b;
            } else if (b == null) {
                soma = a;
            } else {
                soma = a.add(b).setScale(ESCALA, RoundingMode.HALF_UP);
            }
            if (soma != null) {
                nutriente.gravar().accept(resultado, soma);
            }
        }
        return resultado;
    }

    /** Chaves dos nutrientes sem valor nesta composicao. */
    public java.util.List<String> nutrientesAusentes() {
        return Nutriente.TODOS.stream()
                .filter(x -> x.ler().apply(this) == null)
                .map(Nutriente::chave)
                .toList();
    }

    public BigDecimal valorDe(String chaveDoNutriente) {
        Nutriente nutriente = Nutriente.POR_CHAVE.get(chaveDoNutriente);
        return nutriente == null ? null : nutriente.ler().apply(this);
    }

    public void definir(String chaveDoNutriente, BigDecimal valor) {
        Nutriente nutriente = Nutriente.POR_CHAVE.get(chaveDoNutriente);
        if (nutriente != null) {
            nutriente.gravar().accept(this, valor);
        }
    }

    /** Representacao chave-valor, omitindo nutrientes nao determinados. */
    public Map<String, BigDecimal> comoMapa() {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        for (Nutriente nutriente : Nutriente.TODOS) {
            BigDecimal valor = nutriente.ler().apply(this);
            if (valor != null) {
                mapa.put(nutriente.chave(), valor);
            }
        }
        return mapa;
    }

    public boolean vazia() {
        return Nutriente.TODOS.stream().allMatch(x -> x.ler().apply(this) == null);
    }
}
