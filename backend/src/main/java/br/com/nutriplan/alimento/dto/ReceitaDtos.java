package br.com.nutriplan.alimento.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class ReceitaDtos {

    private ReceitaDtos() {
    }

    // ------------------------------------------------------------------ entrada

    public record IngredienteRequest(
            @NotNull Long alimentoId,
            /** Medida caseira escolhida. Nulo quando o ingrediente foi pesado. */
            Long medidaId,
            @NotNull
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantidade
    ) {}

    public record ReceitaRequest(
            @NotBlank @Size(max = 250) String nome,
            @Size(max = 100) String grupo,
            /**
             * Peso da preparacao pronta. Opcional, e a interface avisa quando
             * fica de fora: cozinhar muda o peso, e sem esse numero a
             * composicao por 100 g e uma estimativa, nao uma medida.
             */
            @DecimalMin(value = "0.001", message = "O rendimento deve ser maior que zero")
            BigDecimal rendimentoGramas,
            @Min(value = 1, message = "A receita precisa render ao menos uma porção")
            Integer porcoes,
            @Size(max = 4000) String modoPreparo,
            @NotEmpty(message = "Uma receita precisa de ao menos um ingrediente")
            @Valid List<IngredienteRequest> ingredientes
    ) {}

    // ------------------------------------------------------------------- saida

    public record IngredienteResponse(
            Long id,
            Long alimentoId,
            String descricao,
            String fonteDescricao,
            Long medidaId,
            /** Texto pronto: "2 colheres de sopa" ou "150 g". */
            String quantidade,
            BigDecimal gramas
    ) {}

    /**
     * Receita completa, como o nutricionista a ve.
     *
     * `rendimentoEstimado` e `nutrientesIncompletos` existem para que a tela
     * possa dizer o que o numero nao diz: que o peso final foi presumido, e que
     * parte dos nutrientes e piso e nao total.
     */
    public record ReceitaResponse(
            Long id,
            String nome,
            String grupo,
            String modoPreparo,
            BigDecimal rendimentoGramas,
            boolean rendimentoEstimado,
            BigDecimal pesoDosIngredientes,
            Integer porcoes,
            BigDecimal gramasPorPorcao,
            ComposicaoDto composicaoPor100g,
            ComposicaoDto composicaoDaPorcao,
            Set<String> nutrientesIncompletos,
            List<IngredienteResponse> ingredientes
    ) {}

    public record ReceitaResumo(
            Long id,
            String nome,
            String grupo,
            int totalDeIngredientes,
            BigDecimal rendimentoGramas,
            Integer porcoes,
            BigDecimal energiaKcalPor100g
    ) {}
}
