package br.com.nutriplan.antropometria.dto;

import br.com.nutriplan.antropometria.domain.ClassificacaoImc;
import br.com.nutriplan.antropometria.domain.ClassificacaoInfantil;
import br.com.nutriplan.antropometria.domain.EquacaoGastoEnergetico;
import br.com.nutriplan.antropometria.domain.GanhoGestacional;
import br.com.nutriplan.antropometria.domain.IndicadorDeCrescimento;
import br.com.nutriplan.antropometria.domain.ProtocoloComposicao;
import br.com.nutriplan.antropometria.domain.RiscoCardiometabolico;
import br.com.nutriplan.antropometria.domain.SituacaoDoGanho;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Contratos de entrada e saída do módulo de antropometria. */
public final class AntropometriaDtos {

    private AntropometriaDtos() {
    }

    // ------------------------------------------------------------------ entrada

    /**
     * Medidas de uma avaliação.
     *
     * Todas opcionais exceto a data: o profissional mede o que a consulta
     * permitiu, e exigir o conjunto completo transformaria o registro parcial
     * em registro nenhum.
     */
    public record AvaliacaoRequest(
            @NotNull @PastOrPresent(message = "A avaliação não pode ter data futura")
            LocalDate data,

            @DecimalMin(value = "0.1", message = "O peso deve ser maior que zero") BigDecimal pesoKg,
            @DecimalMin(value = "0.1", message = "A altura deve ser maior que zero") BigDecimal alturaCm,

            Map<String, BigDecimal> dobras,
            Map<String, BigDecimal> circunferencias,

            /** Nulo registra as dobras sem estimar composição. */
            ProtocoloComposicao protocoloComposicao,

            /** Nulo registra a avaliação sem estimar gasto energético. */
            EquacaoGastoEnergetico equacaoGasto,
            @DecimalMin(value = "1.0", message = "O fator de atividade não pode ser menor que 1")
            BigDecimal fatorAtividade,

            @Size(max = 2000) String observacoes,

            /** Semana gestacional, quando a paciente está grávida. */
            @jakarta.validation.constraints.Min(value = 1, message = "A semana gestacional vai de 1 a 42")
            @jakarta.validation.constraints.Max(value = 42, message = "A semana gestacional vai de 1 a 42")
            Integer semanaGestacional,

            /**
             * Peso anterior à gestação. Sem ele o ganho não é classificado: a
             * faixa esperada depende do IMC de antes, e o IMC de hoje já embute
             * o ganho que se quer avaliar.
             */
            @DecimalMin(value = "20.0", message = "O peso pré-gestacional parece baixo demais")
            BigDecimal pesoPreGestacionalKg
    ) {}

    // ------------------------------------------------------------------- saída

    /**
     * Um valor derivado, com o motivo quando não pôde ser calculado.
     *
     * Devolver apenas nulo obrigaria a interface a adivinhar se o dado falta
     * porque a medida não foi feita ou porque a regra impede o cálculo. O
     * motivo é o que permite dizer ao profissional o que fazer a respeito.
     */
    public record Derivado<T>(T valor, String indisponivelPorque) {
        public static <T> Derivado<T> de(T valor) {
            return new Derivado<>(valor, null);
        }

        public static <T> Derivado<T> ausente(String motivo) {
            return new Derivado<>(null, motivo);
        }
    }

    public record ComposicaoCorporalResponse(
            ProtocoloComposicao protocolo,
            String protocoloDescricao,
            BigDecimal percentualGordura,
            BigDecimal massaGordaKg,
            BigDecimal massaMagraKg
    ) {}

    public record GastoEnergeticoResponse(
            EquacaoGastoEnergetico equacao,
            String equacaoDescricao,
            BigDecimal fatorAtividade,
            BigDecimal basalKcal,
            BigDecimal totalKcal
    ) {}

    /**
     * Leitura de um indicador infantil contra as curvas da OMS.
     *
     * `referencia` diz qual das duas curvas foi usada — os padrões de 2006 ou a
     * referência de 2007 —, porque são trabalhos diferentes e a distinção
     * precisa acompanhar o resultado.
     */
    public record IndicadorInfantilResponse(
            IndicadorDeCrescimento indicador,
            String indicadorDescricao,
            BigDecimal escoreZ,
            ClassificacaoInfantil classificacao,
            String classificacaoDescricao,
            boolean exigeAtencao,
            String referencia
    ) {}

    public record CrescimentoInfantilResponse(
            Integer idadeEmMeses,
            List<IndicadorInfantilResponse> indicadores
    ) {}

    /**
     * Ganho de peso na gestação, pelas faixas do IOM 2009.
     *
     * A faixa vem do IMC **pré-gestacional**, e não do atual: o IMC de hoje já
     * embute o ganho que se quer avaliar.
     */
    public record GestacaoResponse(
            Integer semanaGestacional,
            BigDecimal pesoPreGestacionalKg,
            BigDecimal imcPreGestacional,
            GanhoGestacional faixa,
            String faixaDescricao,
            BigDecimal ganhoAteAgora,
            BigDecimal esperadoMin,
            BigDecimal esperadoMax,
            SituacaoDoGanho situacao,
            String situacaoDescricao,
            BigDecimal ganhoTotalRecomendadoMin,
            BigDecimal ganhoTotalRecomendadoMax
    ) {}

    public record AvaliacaoResponse(
            Long id,
            Long pacienteId,
            String pacienteNome,
            LocalDate data,
            BigDecimal pesoKg,
            BigDecimal alturaCm,
            Map<String, BigDecimal> dobras,
            Map<String, BigDecimal> circunferencias,

            BigDecimal imc,
            Derivado<ClassificacaoImc> classificacaoImc,
            BigDecimal relacaoCinturaQuadril,
            Derivado<RiscoCardiometabolico> riscoCardiometabolico,

            ComposicaoCorporalResponse composicao,
            GastoEnergeticoResponse gastoEnergetico,

            /** Presente quando o paciente tem até 19 anos. */
            Derivado<CrescimentoInfantilResponse> crescimentoInfantil,
            /** Presente quando a avaliação informa semana gestacional. */
            Derivado<GestacaoResponse> gestacao,

            String observacoes,
            java.time.Instant criadoEm
    ) {}

    /**
     * Variação de uma medida entre avaliações.
     *
     * @param comparavel falso quando as duas pontas não são comparáveis — medida
     *                   ausente numa delas, ou protocolos de composição distintos
     */
    public record Variacao(
            String medida,
            String rotulo,
            BigDecimal atual,
            BigDecimal anterior,
            BigDecimal diferenca,
            boolean comparavel,
            String observacao
    ) {}

    public record PontoDaEvolucao(
            Long avaliacaoId,
            LocalDate data,
            BigDecimal pesoKg,
            BigDecimal imc,
            BigDecimal percentualGordura,
            ProtocoloComposicao protocolo,
            List<Variacao> variacoesFrenteAAnterior,
            List<Variacao> variacoesFrenteAPrimeira
    ) {}

    public record EvolucaoResponse(
            Long pacienteId,
            String pacienteNome,
            int totalDeAvaliacoes,
            List<PontoDaEvolucao> pontos
    ) {}

    /** Catálogo de protocolos, para a interface montar o formulário. */
    public record ProtocoloResponse(
            ProtocoloComposicao protocolo,
            String descricao,
            boolean exigeSexo,
            boolean exigeIdade,
            List<String> dobrasFemininas,
            List<String> dobrasMasculinas
    ) {}
}
