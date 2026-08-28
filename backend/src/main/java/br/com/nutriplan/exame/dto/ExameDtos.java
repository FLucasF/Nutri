package br.com.nutriplan.exame.dto;

import br.com.nutriplan.exame.domain.ClassificacaoDoExame;
import br.com.nutriplan.exame.domain.Exame;
import br.com.nutriplan.exame.domain.FaixaDeReferencia;
import br.com.nutriplan.exame.domain.ParametroExame;
import br.com.nutriplan.exame.domain.SolicitacaoDeExame;
import br.com.nutriplan.paciente.domain.Sexo;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ExameDtos {

    private ExameDtos() {
    }

    // ------------------------------------------------------------------ entrada

    public record ExameRequest(
            @NotNull Long parametroId,
            @NotNull
            @PastOrPresent(message = "A data de coleta não pode ser futura")
            LocalDate dataColeta,
            /** Nulo significa parametro pedido e ainda nao determinado. */
            BigDecimal valor,
            /** Sobrepõe a unidade padrao do parametro, quando o laudo usa outra. */
            @Size(max = 20) String unidade,
            @Size(max = 1000) String observacao
    ) {}

    public record ParametroRequest(
            @jakarta.validation.constraints.NotBlank @Size(max = 120) String nome,
            @jakarta.validation.constraints.NotBlank @Size(max = 20) String unidadePadrao,
            @Size(max = 60) String grupo,
            BigDecimal minimo,
            BigDecimal maximo
    ) {}

    public record SolicitacaoRequest(
            @NotNull
            @PastOrPresent(message = "A data da solicitação não pode ser futura")
            LocalDate data,
            @NotEmpty(message = "Escolha ao menos um exame") List<Long> parametroIds,
            @Size(max = 1000) String observacao
    ) {}

    // ------------------------------------------------------------------- saida

    public record FaixaResponse(
            Sexo sexo,
            Integer idadeMin,
            Integer idadeMax,
            BigDecimal minimo,
            BigDecimal maximo,
            String texto
    ) {
        public static FaixaResponse de(FaixaDeReferencia f) {
            return new FaixaResponse(f.getSexo(), f.getIdadeMin(), f.getIdadeMax(),
                    f.getMinimo(), f.getMaximo(), f.comoTexto());
        }
    }

    public record ParametroResponse(
            Long id,
            String nome,
            String unidadePadrao,
            String grupo,
            boolean doCatalogoDoSistema,
            boolean editavel,
            List<FaixaResponse> faixas
    ) {
        public static ParametroResponse de(ParametroExame p) {
            return new ParametroResponse(p.getId(), p.getNome(), p.getUnidadePadrao(),
                    p.getGrupo(), p.ehDoCatalogoDoSistema(), !p.ehDoCatalogoDoSistema(),
                    p.getFaixas().stream().map(FaixaResponse::de).toList());
        }
    }

    /**
     * Resultado de um exame.
     *
     * `referencia` e o texto da faixa **usada na entrada**, e nao a faixa
     * cadastrada hoje. E o que permite ler um exame de dois anos atras sabendo
     * contra o que ele foi classificado.
     */
    public record ExameResponse(
            Long id,
            Long parametroId,
            String parametro,
            String grupo,
            LocalDate dataColeta,
            BigDecimal valor,
            String unidade,
            ClassificacaoDoExame classificacao,
            String classificacaoDescricao,
            String referencia,
            String observacao,
            boolean temLaudo,
            String laudoNome
    ) {
        public static ExameResponse de(Exame e) {
            return new ExameResponse(
                    e.getId(), e.getParametro().getId(), e.getParametro().getNome(),
                    e.getParametro().getGrupo(), e.getDataColeta(), e.getValor(), e.getUnidade(),
                    e.getClassificacao(),
                    e.getClassificacao() == null ? null : e.getClassificacao().getDescricao(),
                    e.referenciaComoTexto(), e.getObservacao(),
                    e.temLaudo(), e.getLaudoNome());
        }
    }

    /** Um ponto da serie historica de um parametro. */
    public record PontoDaSerie(
            LocalDate dataColeta,
            BigDecimal valor,
            String unidade,
            ClassificacaoDoExame classificacao,
            /** Variação frente à coleta anterior. Nula na primeira. */
            BigDecimal variacao
    ) {}

    public record SerieResponse(
            Long parametroId,
            String parametro,
            String unidade,
            List<PontoDaSerie> pontos,
            /**
             * Verdadeiro quando a serie tem coletas em unidades diferentes.
             * Nesse caso os pontos nao sao comparaveis entre si, e a interface
             * precisa dizer isso em vez de desenhar uma linha enganosa.
             */
            boolean unidadesMisturadas
    ) {}

    public record SolicitacaoResponse(
            Long id,
            LocalDate data,
            String observacao,
            List<String> exames
    ) {
        public static SolicitacaoResponse de(SolicitacaoDeExame s) {
            return new SolicitacaoResponse(s.getId(), s.getData(), s.getObservacao(),
                    s.getParametros().stream()
                            .map(p -> p.getParametro().getNome())
                            .toList());
        }
    }
}
