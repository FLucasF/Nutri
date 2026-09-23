package br.com.nutriplan.anthropometry.dto;

import br.com.nutriplan.anthropometry.domain.BmiClassification;
import br.com.nutriplan.anthropometry.domain.ChildClassification;
import br.com.nutriplan.anthropometry.domain.EnergyExpenditureEquation;
import br.com.nutriplan.anthropometry.domain.GestationalGain;
import br.com.nutriplan.anthropometry.domain.GrowthIndicator;
import br.com.nutriplan.anthropometry.domain.CompositionProtocol;
import br.com.nutriplan.anthropometry.domain.CardiometabolicRisk;
import br.com.nutriplan.anthropometry.domain.GainStatus;
import br.com.nutriplan.anthropometry.domain.CircumferenceSite;
import br.com.nutriplan.anthropometry.domain.Side;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Input and output contracts of the anthropometry module. */
public final class AnthropometryDtos {

    /**
     * Uma circunferência medida.
     *
     * Local e lado são enums, então a tela não consegue mandar um local que o
     * domínio não conhece — e o lado deixa de ser um sufixo no nome do campo.
     */
    public record CircumferenceValue(
            @NotNull CircumferenceSite site,
            @NotNull Side side,
            @NotNull @DecimalMin(value = "0.1", message = "A circunferência deve ser maior que zero")
            BigDecimal valueCm,
            /** Preenchido só na resposta, para a tela não repetir a tradução. */
            String description
    ) {
        public CircumferenceValue(CircumferenceSite site, Side side, BigDecimal valueCm) {
            this(site, side, valueCm, site.getDescription());
        }
    }

    private AnthropometryDtos() {
    }

    // -------------------------------------------------------------------- input

    /**
     * Measurements of one assessment.
     *
     * All optional except the date: the professional measures what the
     * appointment allowed, and requiring the complete set would turn a partial
     * record into no record at all.
     */
    public record AssessmentRequest(
            @NotNull @PastOrPresent(message = "A avaliação não pode ter data futura")
            LocalDate date,

            @DecimalMin(value = "0.1", message = "O peso deve ser maior que zero") BigDecimal weightKg,
            @DecimalMin(value = "0.1", message = "A altura deve ser maior que zero") BigDecimal heightCm,

            Map<String, BigDecimal> skinfolds,
            /**
             * Circunferências com local e lado.
             *
             * Era um mapa de nome para número, que não tinha onde pôr o lado.
             * Sete dos treze locais são medidos dos dois lados.
             */
            @Valid List<CircumferenceValue> circumferences,

            /** Diâmetros ósseos, em centímetros. */
            BigDecimal diameterHumerus,
            BigDecimal diameterWrist,
            BigDecimal diameterFemur,

            BigDecimal heightSittingCm,
            BigDecimal heightKneeCm,

            /** Bioimpedância, como o aparelho informou. */
            BigDecimal biaFatPercentage,
            BigDecimal biaFatMassKg,
            BigDecimal biaMusclePercentage,
            BigDecimal biaMuscleMassKg,
            BigDecimal biaLeanMassKg,
            BigDecimal biaBoneMassKg,
            BigDecimal biaVisceralFat,
            BigDecimal biaBodyWaterPercentage,
            Integer biaMetabolicAge,

            /** Null records the skinfolds without estimating composition. */
            CompositionProtocol protocolComposition,

            /** Null records the assessment without estimating energy expenditure. */
            EnergyExpenditureEquation equationExpenditure,
            @DecimalMin(value = "1.0", message = "O fator de atividade não pode ser menor que 1")
            BigDecimal factorActivity,

            @Size(max = 8000) String notes,

            /** Gestational week, when the patient is pregnant. */
            @jakarta.validation.constraints.Min(value = 1, message = "A semana gestacional vai de 1 a 42")
            @jakarta.validation.constraints.Max(value = 42, message = "A semana gestacional vai de 1 a 42")
            Integer gestationalWeek,

            /**
             * Pre-pregnancy weight. Without it the gain is not classified: the
             * expected range depends on the earlier BMI, and today's BMI
             * already embeds the very gain being assessed.
             */
            @DecimalMin(value = "20.0", message = "O peso pré-gestacional parece baixo demais")
            BigDecimal weightGestationalPreKg
    ) {}

    // ------------------------------------------------------------------ output

    /**
     * A derived value, with the reason when it could not be calculated.
     *
     * Returning only null would force the interface to guess whether the datum
     * is missing because the measurement was not taken or because the rule
     * prevents the calculation. The reason is what makes it possible to tell
     * the professional what to do about it.
     */
    public record Derived<T>(T value, String unavailableBecause) {
        public static <T> Derived<T> from(T value) {
            return new Derived<>(value, null);
        }

        public static <T> Derived<T> missing(String reason) {
            return new Derived<>(null, reason);
        }
    }

    public record CompositionBodyResponse(
            CompositionProtocol protocol,
            String protocolDescription,
            BigDecimal percentageFat,
            BigDecimal massFatKg,
            BigDecimal massLeanKg
    ) {}

    public record ExpenditureEnergyResponse(
            EnergyExpenditureEquation equation,
            String equationDescription,
            BigDecimal factorActivity,
            BigDecimal basalKcal,
            BigDecimal totalKcal
    ) {}

    /**
     * Reading of a child indicator against the WHO curves.
     *
     * `reference` says which of the two curves was used — the 2006 standards or
     * the 2007 reference — because they are different works and the distinction
     * has to travel with the result.
     */
    public record ChildIndicatorResponse(
            GrowthIndicator indicator,
            String indicatorDescription,
            BigDecimal scoreZ,
            ChildClassification classification,
            String classificationDescription,
            boolean requiresAttention,
            String reference
    ) {}

    public record ChildGrowthResponse(
            Integer ageAtMonths,
            List<ChildIndicatorResponse> indicators
    ) {}

    /**
     * Weight gain in pregnancy, by the IOM 2009 bands.
     *
     * The band comes from the **pre-pregnancy** BMI, and not from the current
     * one: today's BMI already embeds the gain being assessed.
     */
    public record PregnancyResponse(
            Integer gestationalWeek,
            BigDecimal weightGestationalPreKg,
            BigDecimal bmiGestationalPre,
            GestationalGain range,
            String rangeDescription,
            BigDecimal gainAteNow,
            BigDecimal expectedMin,
            BigDecimal expectedMax,
            GainStatus status,
            String statusDescription,
            BigDecimal totalGainRecommendedMin,
            BigDecimal totalGainRecommendedMax
    ) {}

    public record AssessmentResponse(
            Long id,
            Long patientId,
            String patientName,
            LocalDate date,
            BigDecimal weightKg,
            BigDecimal heightCm,
            /**
             * Idade na data da avaliação.
             *
             * Sai daqui porque o servidor já a calcula para classificar o IMC, e
             * a tela precisa dela para saber se trava a altura: o cliente pede
             * altura imutável para adultos, e criança ainda cresce.
             */
            Integer ageYears,
            Map<String, BigDecimal> skinfolds,
            List<CircumferenceValue> circumferences,

            BigDecimal diameterHumerus,
            BigDecimal diameterWrist,
            BigDecimal diameterFemur,
            BigDecimal heightSittingCm,
            BigDecimal heightKneeCm,

            BigDecimal biaFatPercentage,
            BigDecimal biaFatMassKg,
            BigDecimal biaMusclePercentage,
            BigDecimal biaMuscleMassKg,
            BigDecimal biaLeanMassKg,
            BigDecimal biaBoneMassKg,
            BigDecimal biaVisceralFat,
            BigDecimal biaBodyWaterPercentage,
            Integer biaMetabolicAge,

            BigDecimal bmi,
            Derived<BmiClassification> classificationBmi,
            BigDecimal ratioWaistHip,
            Derived<CardiometabolicRisk> riskCardiometabolico,

            CompositionBodyResponse composition,
            ExpenditureEnergyResponse expenditureEnergy,

            /** Present when the patient is 19 or younger. */
            Derived<ChildGrowthResponse> childGrowth,
            /** Present when the assessment reports a gestational week. */
            Derived<PregnancyResponse> pregnancy,

            String notes,
            java.time.Instant createdAt
    ) {}

    /**
     * Variation of one measurement between assessments.
     *
     * @param comparable false when the two ends are not comparable — measurement
     *                   absent at one of them, or different composition protocols
     */
    public record Change(
            String measure,
            String label,
            BigDecimal current,
            BigDecimal previous,
            BigDecimal difference,
            boolean comparable,
            String notes
    ) {}

    public record ProgressPoint(
            Long assessmentId,
            LocalDate date,
            BigDecimal weightKg,
            BigDecimal bmi,
            BigDecimal percentageFat,
            CompositionProtocol protocol,
            List<Change> changesPreviousFront,
            List<Change> changesFrontFirst
    ) {}

    public record ProgressResponse(
            Long patientId,
            String patientName,
            int assessmentsTotal,
            List<ProgressPoint> points
    ) {}

    /** Catalog of protocols, for the interface to build the form. */
    public record ProtocolResponse(
            CompositionProtocol protocol,
            String description,
            boolean requiresSex,
            boolean requiresAge,
            List<String> skinfoldsFemale,
            List<String> skinfoldsMale
    ) {}
}
