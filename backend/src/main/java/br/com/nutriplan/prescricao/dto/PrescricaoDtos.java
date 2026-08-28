package br.com.nutriplan.prescricao.dto;

import br.com.nutriplan.alimento.dto.ComposicaoDto;
import br.com.nutriplan.prescricao.domain.MetodoPrescricao;
import br.com.nutriplan.prescricao.domain.StatusPlano;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Contratos de entrada e saida do modulo de prescricao. */
public final class PrescricaoDtos {

    private PrescricaoDtos() {
    }

    // ------------------------------------------------------------------ entrada

    public record EquivalenteRequest(
            Long alimentoId,
            Long medidaId,
            @NotBlank @Size(max = 250) String descricao,
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantidade
    ) {}

    public record ItemRequest(
            Long alimentoId,
            Long medidaId,
            @Size(max = 250) String descricao,
            @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero")
            BigDecimal quantidade,
            @Size(max = 500) String observacao,
            @Valid List<EquivalenteRequest> equivalentes
    ) {}

    public record RefeicaoRequest(
            @NotBlank @Size(max = 100) String nome,
            LocalTime horario,
            @Size(max = 1000) String observacao,
            @Valid List<ItemRequest> itens
    ) {}

    public record PlanoRequest(
            @NotBlank @Size(max = 150) String titulo,
            Long pacienteId,
            @NotNull MetodoPrescricao metodo,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            @Size(max = 4000) String orientacoes,
            @Size(max = 4000) String observacoesInternas,
            @DecimalMin(value = "0.0", message = "A meta energética não pode ser negativa")
            BigDecimal metaEnergiaKcal,
            boolean modelo,
            @Valid List<RefeicaoRequest> refeicoes
    ) {}

    /** Criacao de plano a partir de um modelo existente. */
    public record AplicarModeloRequest(
            @NotNull Long modeloId,
            @NotNull Long pacienteId,
            @Size(max = 150) String titulo
    ) {}

    // ------------------------------------------------------------------- saida

    /**
     * Total nutricional com a sua propria margem de confianca.
     *
     * `nutrientesIncompletos` lista o que foi somado a partir de apenas parte
     * dos itens — nesses casos o valor e um piso, e a interface precisa dizer
     * isso em vez de exibir um numero que aparenta exatidao.
     */
    public record TotalResponse(
            ComposicaoDto composicao,
            int itensNoCalculo,
            int itensForaDoCalculo,
            Set<String> nutrientesIncompletos,
            Set<String> nutrientesSemDado,
            boolean confiavel,
            DistribuicaoResponse distribuicao,
            BigDecimal adequacaoEnergeticaPct
    ) {}

    public record DistribuicaoResponse(
            BigDecimal proteinaPct,
            BigDecimal carboidratoPct,
            BigDecimal lipideoPct,
            BigDecimal energiaCalculadaKcal
    ) {}

    public record EquivalenteResponse(
            Long id,
            Long alimentoId,
            String descricao,
            String porcao,
            BigDecimal gramas
    ) {}

    public record ItemResponse(
            Long id,
            Long alimentoId,
            Long medidaId,
            String descricao,
            String porcao,
            BigDecimal quantidade,
            BigDecimal gramas,
            Integer ordem,
            String observacao,
            List<EquivalenteResponse> equivalentes
    ) {}

    public record RefeicaoResponse(
            Long id,
            String nome,
            LocalTime horario,
            Integer ordem,
            String observacao,
            List<ItemResponse> itens,
            TotalResponse total
    ) {}

    /** Plano completo, como o nutricionista o ve. */
    public record PlanoResponse(
            Long id,
            String titulo,
            Long pacienteId,
            String pacienteNome,
            MetodoPrescricao metodo,
            String metodoDescricao,
            StatusPlano status,
            String statusDescricao,
            String identificadorPublico,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String orientacoes,
            String observacoesInternas,
            BigDecimal metaEnergiaKcal,
            boolean modelo,
            List<RefeicaoResponse> refeicoes,
            TotalResponse totalDoDia,
            java.time.Instant criadoEm,
            java.time.Instant atualizadoEm
    ) {}

    /** Linha de listagem. */
    public record PlanoResumo(
            Long id,
            String titulo,
            Long pacienteId,
            String pacienteNome,
            MetodoPrescricao metodo,
            StatusPlano status,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            boolean modelo,
            int refeicoes,
            int itens,
            BigDecimal energiaKcal,
            java.time.Instant atualizadoEm
    ) {}

    /**
     * Plano como o paciente o ve, pelo link.
     *
     * Deliberadamente diferente do PlanoResponse: sem observacoes internas, sem
     * identificadores de conta e sem os avisos tecnicos de cobertura de dado.
     * O paciente recebe o que deve comer, nao o diagnostico da base nutricional.
     */
    public record PlanoPublicoResponse(
            String titulo,
            String pacienteNome,
            String nutricionistaNome,
            String nutricionistaCrn,
            String consultorioNome,
            String corPrimaria,
            String logoUrl,
            MetodoPrescricao metodo,
            boolean vigente,
            boolean encerrado,
            LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String orientacoes,
            /** Orientações anexadas ao plano, no texto congelado no anexo. */
            List<OrientacaoPublicaResponse> orientacoesAnexadas,
            List<RefeicaoPublicaResponse> refeicoes,
            ResumoPublicoResponse resumo
    ) {}

    /**
     * Orientacao entregue no plano.
     *
     * {@code imagem} e o endereco da figura ja pronto para o {@code src}, nulo
     * quando nao ha figura. Vem montado daqui, e nao do navegador, para que a
     * pagina do paciente nao precise conhecer o formato da rota.
     */
    public record OrientacaoPublicaResponse(Long id, String titulo, String corpo,
                                            String imagem) {}

    public record RefeicaoPublicaResponse(
            String nome,
            LocalTime horario,
            String observacao,
            List<ItemPublicoResponse> itens
    ) {}

    public record ItemPublicoResponse(
            String descricao,
            String porcao,
            /**
             * Peso da porcao. Vai junto com o texto da medida caseira porque a
             * pagina do paciente mostra as duas coisas com pesos visuais
             * diferentes: a medida grande, porque e a instrucao, e a grama
             * pequena, porque e o registro. Nulo quando o item nao tem peso
             * definido ("a vontade").
             */
            BigDecimal pesoGramas,
            String observacao,
            List<EquivalentePublicoResponse> substituicoes
    ) {}

    public record EquivalentePublicoResponse(String descricao, String porcao) {}

    /** Resumo do dia em linguagem de paciente: so o essencial. */
    public record ResumoPublicoResponse(
            BigDecimal energiaKcal,
            BigDecimal proteinaG,
            BigDecimal carboidratoG,
            BigDecimal lipideosG,
            int refeicoes
    ) {}
}
