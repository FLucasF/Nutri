package br.com.nutriplan.alimento.dto;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** DTOs do modulo de alimentos, agrupados por serem pequenos e coesos. */
public final class AlimentoDtos {

    private AlimentoDtos() {
    }

    /** Linha de listagem: o suficiente para escolher um alimento na busca. */
    public record Resumo(
            Long id,
            String descricao,
            String grupo,
            FonteDeDados fonte,
            String marca,
            BigDecimal energiaKcal,
            BigDecimal proteinaG,
            BigDecimal carboidratoG,
            BigDecimal lipideosG,
            boolean basePublica
    ) {
        public static Resumo de(Alimento a) {
            var c = a.getComposicao();
            return new Resumo(a.getId(), a.getDescricao(), a.getGrupo(), a.getFonte(), a.getMarca(),
                    c.getEnergiaKcal(), c.getProteinaG(), c.getCarboidratoG(), c.getLipideosG(),
                    a.ehBasePublica());
        }
    }

    /**
     * @param doAcervoBase porcao que veio com o sistema, comum a todos os consultorios
     * @param editavel     falso para porcoes do acervo base: o consultorio pode criar
     *                     a propria versao, mas nao alterar a de todo mundo
     */
    public record MedidaResponse(
            Long id,
            String descricao,
            BigDecimal gramas,
            boolean padrao,
            boolean doAcervoBase,
            boolean editavel
    ) {
        public static MedidaResponse de(MedidaCaseira m, Long contaId) {
            return new MedidaResponse(m.getId(), m.getDescricao(), m.getGramas(), m.isPadrao(),
                    m.ehDoAcervoBase(), m.editavelPor(contaId));
        }
    }

    /** Detalhe completo, com a composicao e as porcoes usuais. */
    public record Detalhe(
            Long id,
            String descricao,
            String grupo,
            FonteDeDados fonte,
            String fonteDescricao,
            String codigoFonte,
            /** EAN do produto industrializado. Nulo nas tabelas de referencia. */
            String codigoBarras,
            String marca,
            boolean basePublica,
            boolean editavel,
            ComposicaoDto composicao,
            List<MedidaResponse> medidas
    ) {
        /**
         * As medidas vem de fora, e nao de a.getMedidas(): a associacao JPA
         * traria as porcoes de todos os consultorios. A lista visivel para esta
         * conta e resolvida por consulta filtrada no repositorio.
         */
        public static Detalhe de(Alimento a, List<MedidaCaseira> medidasVisiveis, Long contaId) {
            return new Detalhe(
                    a.getId(), a.getDescricao(), a.getGrupo(),
                    a.getFonte(), a.getFonte().getDescricao(), a.getCodigoFonte(),
                    a.getCodigoBarras(), a.getMarca(),
                    a.ehBasePublica(),
                    // Base publica e referencia compartilhada: ninguem edita.
                    !a.ehBasePublica(),
                    ComposicaoDto.de(a.getComposicao()),
                    medidasVisiveis.stream().map(m -> MedidaResponse.de(m, contaId)).toList());
        }
    }

    public record MedidaRequest(
            @NotBlank @Size(max = 120) String descricao,
            @NotNull @DecimalMin(value = "0.001", message = "O peso da medida deve ser maior que zero")
            BigDecimal gramas,
            boolean padrao
    ) {}

    /** Cadastro de alimento proprio do consultorio. */
    public record AlimentoRequest(
            @NotBlank @Size(max = 250) String descricao,
            @Size(max = 100) String grupo,
            /**
             * EAN do produto, quando houver. Permite que o consultorio encontre
             * pelo codigo o proprio produto — o que a base colaborativa nao tem
             * ou traz com dado que o nutricionista nao aceita.
             */
            @Size(max = 20) String codigoBarras,
            @Size(max = 100) String marca,
            @NotNull ComposicaoDto composicao,
            @Valid List<MedidaRequest> medidas
    ) {}

    /**
     * Resumo de uma importacao de tabela, para exibir ao nutricionista o que
     * entrou, o que foi descartado e por que.
     */
    public record ResultadoImportacao(int importados, int ignorados, List<String> avisos) {}

    /**
     * Resultado do calculo de uma porcao: quanto de cada nutriente ha na
     * quantidade pedida.
     */
    public record PorcaoCalculada(
            Long alimentoId,
            String descricao,
            BigDecimal gramas,
            String medidaUsada,
            ComposicaoDto composicao
    ) {}
}
