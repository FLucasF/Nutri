package br.com.nutriplan.energy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import br.com.nutriplan.anthropometry.domain.BmiClassification;
import br.com.nutriplan.anthropometry.repository.AnthropometricAssessmentRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.energy.domain.ActivityLevel;
import br.com.nutriplan.energy.domain.EnergyEquation;
import br.com.nutriplan.energy.domain.EnergyPlan;
import br.com.nutriplan.energy.domain.EnergyPlanEquation;
import br.com.nutriplan.energy.dto.EnergyDtos;
import br.com.nutriplan.energy.repository.EnergyPlanRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The energy calculation.
 *
 * Three decisions here are worth naming, because each one is a place where the
 * obvious implementation would be wrong:
 *
 *   1. The average is taken over <i>daily totals</i>, never over basal rates.
 *      Averaging a Harris-Benedict basal with an EER total would add a number
 *      that counts activity to one that does not, and the result would mean
 *      nothing. Each equation is brought to the same question first.
 *   2. The activity factor is applied only to basal equations. The EER
 *      equations carry the activity level in their own coefficients, and
 *      multiplying again would count the same movement twice.
 *   3. Everything that goes into the calculation is copied into the record.
 *      Weight, height and age come from an assessment that keeps changing;
 *      reading them live would make an old prescription answer a new number.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnergyPlanService {

    private final EnergyPlanRepository planRepository;
    private final PatientRepository patientRepository;
    private final AnthropometricAssessmentRepository assessmentRepository;
    private final CurrentContext currentContext;
    private final ObjectMapper mapper;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<EnergyDtos.EnergyPlanSummary> ofPatient(Long patientId) {
        Long accountId = currentContext.accountId();
        requirePatient(patientId, accountId);
        return planRepository.ofPatient(accountId, patientId).stream()
                .map(EnergyDtos.EnergyPlanSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnergyDtos.EnergyPlanResponse detail(Long id) {
        EnergyPlan plan = require(id);
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()));
    }

    /** What the pickers offer. It comes from the enums, so it cannot drift. */
    public EnergyDtos.OptionsResponse options() {
        return new EnergyDtos.OptionsResponse(
                java.util.Arrays.stream(EnergyEquation.values())
                        .map(equation -> new EnergyDtos.EquationOption(equation,
                                equation.getDescription(), equation.isTotal(),
                                equation.getAgeMinimum()))
                        .toList(),
                java.util.Arrays.stream(ActivityLevel.values())
                        .map(level -> new EnergyDtos.ActivityOption(level, level.getDescription()))
                        .toList());
    }

    // ------------------------------------------------------------------ escrita

    @Transactional
    public EnergyDtos.EnergyPlanResponse create(EnergyDtos.EnergyPlanRequest request) {
        Long accountId = currentContext.accountId();
        Patient patient = requirePatient(request.patientId(), accountId);

        var plan = new EnergyPlan(accountId, patient.getId(),
                name(request, patient), request.date());
        apply(plan, request, patient);
        planRepository.save(plan);
        log.info("Cálculo energético criado: id={} paciente={} kcal={}",
                plan.getId(), plan.getPatientId(), plan.getPrescribedKcal());
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()));
    }

    @Transactional
    public EnergyDtos.EnergyPlanResponse update(Long id, EnergyDtos.EnergyPlanRequest request) {
        EnergyPlan plan = require(id);
        Patient patient = requirePatient(plan.getPatientId(), plan.getAccountId());
        plan.setName(name(request, patient));
        plan.setDate(request.date());
        apply(plan, request, patient);
        planRepository.save(plan);
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()));
    }

    @Transactional
    public void remove(Long id) {
        EnergyPlan plan = require(id);
        planRepository.delete(plan);
        log.info("Cálculo energético removido: id={}", id);
    }

    // ------------------------------------------------------------------ o cálculo

    private void apply(EnergyPlan plan, EnergyDtos.EnergyPlanRequest request, Patient patient) {
        Measurements measurements = measurements(request, patient);

        plan.setAssessmentId(request.assessmentId());
        plan.setWeightKg(measurements.weightKg());
        plan.setHeightCm(measurements.heightCm());
        plan.setAgeYears(measurements.age());
        plan.setSex(patient.getSex());
        plan.setActivityLevel(request.activityLevel());
        plan.setInjuryFactor(request.injuryFactor() == null ? BigDecimal.ONE : request.injuryFactor());
        plan.setMetKcal(request.metKcal());
        plan.setNotes(RichTextDocument.ofTextOrDocument(request.notes(), mapper).json());

        var input = new EnergyEquation.Input(
                measurements.weightKg().doubleValue(),
                measurements.heightCm().doubleValue(),
                patient.getSex(),
                measurements.age(),
                request.activityLevel());

        plan.clearEquations();
        BigDecimal sum = BigDecimal.ZERO;
        int order = 1;
        for (EnergyEquation equation : request.equations().stream().distinct().toList()) {
            if (!equation.servesAge(measurements.age())) {
                throw new BusinessRuleException(
                        "A equação " + equation.getDescription() + " não foi publicada para "
                                + measurements.age() + " anos. Escolha outra.");
            }
            BigDecimal total = round(equation.totalExpenditure(input));
            BigDecimal basal = equation.isTotal() ? null : round(equation.basal(input));
            plan.add(new EnergyPlanEquation(equation, basal, total, order));
            sum = sum.add(total);
            order++;
        }

        // A média é sobre o gasto do dia, que é a mesma pergunta para todas.
        BigDecimal average = sum.divide(
                BigDecimal.valueOf(plan.getEquations().size()), 2, RoundingMode.HALF_UP);
        plan.setAverageKcal(average);

        plan.setTargetWeightKg(request.targetWeightKg());
        plan.setTargetDate(request.targetDate());
        BigDecimal adjustment = adjustment(request, measurements.weightKg(), plan.getDate());
        plan.setAdjustmentKcal(adjustment);

        BigDecimal prescribed = average.multiply(plan.getInjuryFactor());
        if (plan.getMetKcal() != null) {
            prescribed = prescribed.add(plan.getMetKcal());
        }
        if (adjustment != null) {
            prescribed = prescribed.add(adjustment);
        }
        plan.setPrescribedKcal(prescribed.setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO));
    }

    /**
     * O que a programação de peso tira, ou acrescenta, no dia.
     *
     * Método do Valor Energético do Tecido Adiposo: cada quilo de tecido
     * adiposo vale cerca de 7 700 kcal, distribuídas pelos dias até a data
     * alvo. O exemplo do próprio cliente confere — 10 kg em 90 dias dão as 855
     * kcal por dia que ele cita.
     *
     * @return negativo para perda de peso, nulo quando não há programação
     */
    private BigDecimal adjustment(EnergyDtos.EnergyPlanRequest request,
                                  BigDecimal weightKg, LocalDate from) {
        if (request.targetWeightKg() == null || request.targetDate() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(from, request.targetDate());
        if (days <= 0) {
            throw new BusinessRuleException(
                    "A data alvo precisa ser depois da data do cálculo.");
        }
        BigDecimal difference = request.targetWeightKg().subtract(weightKg);
        if (difference.signum() == 0) {
            return null;
        }
        return difference.multiply(EnergyPlan.KCAL_PER_KG_ADIPOSE)
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    /**
     * A faixa de peso que mantém o adulto em eutrofia, pela altura.
     *
     * O limite inferior é 18,5 — é a dúvida que o cliente registra no
     * documento, e a resposta está na própria tabela de classificação do IMC
     * que o sistema já usa.
     */
    private EnergyDtos.HealthyWeight healthyWeight(BigDecimal heightCm) {
        if (heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        Double minimum = BmiClassification.NORMAL.getLimitInferior();
        Double maximum = BmiClassification.NORMAL.getLimitSuperior();
        if (minimum == null || maximum == null) {
            return null;
        }
        BigDecimal metresSquared = heightCm
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .pow(2);
        return new EnergyDtos.HealthyWeight(
                BigDecimal.valueOf(minimum).multiply(metresSquared).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(maximum).multiply(metresSquared).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(minimum),
                BigDecimal.valueOf(maximum));
    }

    // ------------------------------------------------------------------ entradas

    private record Measurements(BigDecimal weightKg, BigDecimal heightCm, int age) {}

    /**
     * Weight and height come from the assessment when one is named, and from
     * the form when it is not. The form wins over the assessment, because the
     * professional may be calculating for a weight that was not measured —
     * a target weight, for instance.
     */
    private Measurements measurements(EnergyDtos.EnergyPlanRequest request, Patient patient) {
        BigDecimal weight = request.weightKg();
        BigDecimal height = request.heightCm();

        if (request.assessmentId() != null) {
            AnthropometricAssessment assessment = assessmentRepository
                    .findById(request.assessmentId())
                    .filter(found -> found.getAccountId().equals(patient.getAccountId()))
                    .filter(found -> found.getPatientId().equals(patient.getId()))
                    .orElseThrow(() -> new NotFoundException(
                            "Avaliação antropométrica", request.assessmentId()));
            if (weight == null) weight = assessment.getWeightKg();
            if (height == null) height = assessment.getHeightCm();
        }

        if (weight == null || height == null) {
            throw new BusinessRuleException(
                    "O cálculo precisa de peso e altura. Informe os dois, ou escolha uma "
                            + "avaliação antropométrica que já os tenha.");
        }
        if (patient.getSex() == null) {
            throw new BusinessRuleException(
                    "Todas as equações de gasto energético dependem do sexo biológico. "
                            + "Informe-o no cadastro do paciente.");
        }
        if (patient.getDateBirth() == null) {
            throw new BusinessRuleException(
                    "As equações dependem da idade. Informe a data de nascimento do paciente.");
        }
        int age = Period.between(patient.getDateBirth(), request.date()).getYears();
        if (age < 0) {
            throw new BusinessRuleException(
                    "A data do cálculo é anterior ao nascimento do paciente.");
        }
        return new Measurements(weight, height, age);
    }

    private static String name(EnergyDtos.EnergyPlanRequest request, Patient patient) {
        if (StringUtils.hasText(request.name())) {
            return request.name().trim();
        }
        return "Cálculo de " + patient.getName();
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private EnergyPlan require(Long id) {
        return planRepository.find(id, currentContext.accountId())
                .orElseThrow(() -> new NotFoundException("Cálculo energético", id));
    }

    private Patient requirePatient(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }
}
