package br.com.nutriplan.labtest.dto;

import br.com.nutriplan.labtest.domain.LabtestClassification;
import br.com.nutriplan.labtest.domain.Labtest;
import br.com.nutriplan.labtest.domain.ReferenceRange;
import br.com.nutriplan.labtest.domain.LabtestParameter;
import br.com.nutriplan.labtest.domain.LabtestOrder;
import jakarta.validation.Valid;
import br.com.nutriplan.labtest.domain.OrderedParameter;
import br.com.nutriplan.patient.domain.Sex;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class LabtestDtos {

    /** Um painel do consultório, montado a partir dos parâmetros escolhidos. */
    public record PanelRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.NotEmpty java.util.List<Long> parameterIds
    ) {}

    public record PanelParameterResponse(Long id, String name, String unit, String group) {
        public static PanelParameterResponse from(
                br.com.nutriplan.labtest.domain.LabtestPanelParameter item) {
            var parameter = item.getParameter();
            return new PanelParameterResponse(parameter.getId(), parameter.getName(),
                    parameter.getUnitStandard(), parameter.getGroup());
        }
    }

    public record PanelResponse(
            Long id,
            String name,
            /** Painel do sistema não é editável: é acervo compartilhado. */
            boolean systemPanel,
            /** A TAG que a tela mostra para separar os dele dos do sistema. */
            boolean own,
            java.util.List<PanelParameterResponse> parameters
    ) {
        public static PanelResponse from(br.com.nutriplan.labtest.domain.LabtestPanel panel) {
            return new PanelResponse(panel.getId(), panel.getName(),
                    panel.isSystemPanel(), !panel.isSystemPanel(),
                    panel.getParameters().stream()
                            .map(PanelParameterResponse::from)
                            .toList());
        }
    }

    private LabtestDtos() {
    }

    // -------------------------------------------------------------------- input

    public record LabtestRequest(
            @NotNull Long parameterId,
            @NotNull
            @PastOrPresent(message = "A data de coleta não pode ser futura")
            LocalDate dateCollection,
            /** Null means a parameter that was ordered and not yet determined. */
            BigDecimal value,
            /** Overrides the parameter's standard unit, when the report uses another. */
            @Size(max = 20) String unit,
            @Size(max = 1000) String notes
    ) {}

    public record ParameterRequest(
            @jakarta.validation.constraints.NotBlank @Size(max = 120) String name,
            @jakarta.validation.constraints.NotBlank @Size(max = 20) String unitStandard,
            @Size(max = 60) String group,
            BigDecimal minimum,
            BigDecimal maximum
    ) {}

    /**
     * Uma faixa de referência declarada pelo consultório.
     *
     * Faixa de referência varia de laboratório e de método — é por isso que o
     * sistema não traz uma para cada um dos 154 parâmetros, e por isso que o
     * registro congela a que valeu. O consultório cadastra a do laboratório com
     * que trabalha, e a partir daí a classificação sai sozinha.
     *
     * Sexo e faixa etária são opcionais. Sem eles, a faixa vale para todos; com
     * eles, ela tem precedência sobre a geral.
     */
    public record RangeRequest(
            br.com.nutriplan.patient.domain.Sex sex,
            Integer ageMin,
            Integer ageMax,
            BigDecimal minimum,
            BigDecimal maximum
    ) {}

    /** Um exame dentro do pedido: de que painel veio, e se está ligado. */
    public record OrderItemRequest(
            @NotNull Long parameterId,
            @Size(max = 120) String panelName,
            /** Nulo vale como ligado. */
            Boolean active
    ) {
        public boolean isActive() {
            return active == null || active;
        }
    }

    /**
     * O pedido. Aceita a lista simples de ids (tudo ligado, sem painel) ou os
     * itens com painel e interruptor; ao menos uma das duas.
     */
    public record OrderRequest(
            @NotNull
            @PastOrPresent(message = "A data da solicitação não pode ser futura")
            LocalDate date,
            List<Long> parameterIds,
            @Valid List<OrderItemRequest> items,
            @Size(max = 1000) String notes
    ) {}

    /** Religar, desligar ou trocar os exames de um pedido já entregue. */
    public record OrderUpdateRequest(
            @Valid @NotEmpty(message = "Escolha ao menos um exame") List<OrderItemRequest> items,
            @Size(max = 1000) String notes
    ) {}

    // ------------------------------------------------------------------ output

    public record RangeResponse(
            Sex sex,
            Integer ageMin,
            Integer ageMax,
            BigDecimal minimum,
            BigDecimal maximum,
            String text
    ) {
        public static RangeResponse from(ReferenceRange f) {
            return new RangeResponse(f.getSex(), f.getAgeMin(), f.getAgeMax(),
                    f.getMinimum(), f.getMaximum(), f.asText());
        }
    }

    public record ParameterResponse(
            Long id,
            String name,
            String unitStandard,
            String group,
            boolean doSystemCatalog,
            boolean editable,
            List<RangeResponse> ranges
    ) {
        public static ParameterResponse from(LabtestParameter p) {
            return new ParameterResponse(p.getId(), p.getName(), p.getUnitStandard(),
                    p.getGroup(), p.isDoSystemCatalog(), !p.isDoSystemCatalog(),
                    p.getRanges().stream().map(RangeResponse::from).toList());
        }
    }

    /**
     * Result of one lab test.
     *
     * `reference` is the text of the range **used at entry**, and not the range
     * registered today. It is what makes it possible to read a test from two
     * years ago knowing what it was classified against.
     */
    public record LabtestResponse(
            Long id,
            Long parameterId,
            String parameter,
            String group,
            LocalDate dateCollection,
            BigDecimal value,
            String unit,
            LabtestClassification classification,
            String classificationDescription,
            String reference,
            String notes,
            boolean hasReport,
            String reportName
    ) {
        public static LabtestResponse from(Labtest e) {
            return new LabtestResponse(
                    e.getId(), e.getParameter().getId(), e.getParameter().getName(),
                    e.getParameter().getGroup(), e.getDateCollection(), e.getValue(), e.getUnit(),
                    e.getClassification(),
                    e.getClassification() == null ? null : e.getClassification().getDescription(),
                    e.referenceAsText(), e.getNotes(),
                    e.hasReport(), e.getReportName());
        }
    }

    /** One point of a parameter's historical series. */
    public record SeriesPoint(
            LocalDate dateCollection,
            BigDecimal value,
            String unit,
            LabtestClassification classification,
            /** Variation against the previous collection. Null on the first one. */
            BigDecimal change
    ) {}

    public record SeriesResponse(
            Long parameterId,
            String parameter,
            String unit,
            List<SeriesPoint> points,
            /**
             * True when the series has collections in different units. In that
             * case the points are not comparable to each other, and the
             * interface has to say so instead of drawing a misleading line.
             */
            boolean unitsMixed
    ) {}

    public record OrderedParameterResponse(
            Long id,
            Long parameterId,
            String name,
            String unit,
            String group,
            String panelName,
            boolean active
    ) {
        public static OrderedParameterResponse from(OrderedParameter item) {
            var parameter = item.getParameter();
            return new OrderedParameterResponse(item.getId(), parameter.getId(),
                    parameter.getName(), parameter.getUnitStandard(), parameter.getGroup(),
                    item.getPanelName(), item.isActive());
        }
    }

    public record OrderResponse(
            Long id,
            LocalDate date,
            String notes,
            /** Só os ligados, pelo nome: é o que vai para o PDF. */
            List<String> labtests,
            List<OrderedParameterResponse> items
    ) {
        public static OrderResponse from(LabtestOrder s) {
            return new OrderResponse(s.getId(), s.getDate(), s.getNotes(),
                    s.getParameters().stream()
                            .filter(OrderedParameter::isActive)
                            .map(p -> p.getParameter().getName())
                            .toList(),
                    s.getParameters().stream()
                            .map(OrderedParameterResponse::from)
                            .toList());
        }
    }
}
