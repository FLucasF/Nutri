package br.com.nutriplan.energy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import br.com.nutriplan.anthropometry.domain.HealthyWeightRange;
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
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()), warnings(plan));
    }

    /** What the pickers offer. It comes from the enums, so it cannot drift. */
    public EnergyDtos.OptionsResponse options() {
        return new EnergyDtos.OptionsResponse(
                java.util.Arrays.stream(EnergyEquation.values())
                        .map(equation -> new EnergyDtos.EquationOption(equation,
                                equation.getDescription(), equation.isTotal(),
                                equation.getAgeMinimum(), equation.requiresLeanMass()))
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
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()), warnings(plan));
    }

    @Transactional
    public EnergyDtos.EnergyPlanResponse update(Long id, EnergyDtos.EnergyPlanRequest request) {
        EnergyPlan plan = require(id);
        Patient patient = requirePatient(plan.getPatientId(), plan.getAccountId());
        plan.setName(name(request, patient));
        plan.setDate(request.date());
        apply(plan, request, patient);
        planRepository.save(plan);
        return EnergyDtos.EnergyPlanResponse.from(plan, healthyWeight(plan.getHeightCm()), warnings(plan));
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
        plan.setLeanMassKg(measurements.leanMassKg());
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
                request.activityLevel(),
                measurements.leanMassKg() == null ? null : measurements.leanMassKg().doubleValue());

        List<String> needLean = request.equations().stream().distinct()
                .filter(EnergyEquation::requiresLeanMass)
                .map(EnergyEquation::getDescription)
                .toList();
        if (!needLean.isEmpty() && measurements.leanMassKg() == null) {
            throw new BusinessRuleException(String.join(", ", needLean)
                    + (needLean.size() == 1 ? " parte" : " partem")
                    + " da massa magra. Escolha uma avaliação com composição corporal ou "
                    + "bioimpedância, ou informe a massa magra.");
        }

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
     * Quanto uma perda de 1 kg por semana desconta do dia: 7 700 kcal em 7 dias.
     * Acima disso o cardápio deixa de ser cumprível, e é o limiar que faz o
     * aviso aparecer.
     */
    private static final BigDecimal KCAL_PER_DAY_ONE_KG_A_WEEK = BigDecimal.valueOf(1100);

    /**
     * Os avisos sobre o número prescrito.
     *
     * O cliente relatou um cardápio "de 10 kcal": a programação de peso é
     * peso × 7 700 kcal dividido pelos dias até a data alvo, e uma meta
     * agressiva desconta mais do que o gasto do dia. Ele pediu para não
     * limitar — a decisão clínica é dele — e sim avisar. Então o cálculo segue
     * igual, e o resultado vem acompanhado do que o explica.
     *
     * O basal de referência é o de Mifflin-St Jeor, recalculado aqui só para
     * o aviso: as equações escolhidas podem ser todas de gasto total (a EER
     * não tem basal), e mesmo assim o profissional precisa saber quando o
     * prescrito ficou abaixo do que o corpo gasta em repouso.
     */
    private List<String> warnings(EnergyPlan plan) {
        List<String> warnings = new ArrayList<>();
        BigDecimal adjustment = plan.getAdjustmentKcal();
        if (adjustment != null && adjustment.signum() < 0) {
            BigDecimal perDay = adjustment.abs();
            if (perDay.compareTo(KCAL_PER_DAY_ONE_KG_A_WEEK) > 0) {
                BigDecimal kgPerWeek = perDay.multiply(BigDecimal.valueOf(7))
                        .divide(EnergyPlan.KCAL_PER_KG_ADIPOSE, 1, RoundingMode.HALF_UP);
                warnings.add(("A programação de peso desconta %s kcal por dia, o que corresponde a "
                        + "perder %s kg por semana. Acima de 1 kg por semana (1.100 kcal/dia) o "
                        + "cardápio fica difícil de cumprir; confira o peso alvo e a data.")
                        .formatted(number(perDay), number(kgPerWeek)));
            }
        }
        if (plan.getPrescribedKcal() != null && plan.getPrescribedKcal().signum() == 0) {
            warnings.add("A programação de peso desconta mais do que o gasto do dia: o prescrito "
                    + "ficou em zero. O cardápio não pode partir deste número.");
            return warnings;
        }
        if (plan.getWeightKg() != null && plan.getHeightCm() != null
                && plan.getAgeYears() != null && plan.getSex() != null
                && plan.getPrescribedKcal() != null) {
            var input = new EnergyEquation.Input(
                    plan.getWeightKg().doubleValue(), plan.getHeightCm().doubleValue(),
                    plan.getSex(), plan.getAgeYears(), plan.getActivityLevel());
            BigDecimal basal = round(EnergyEquation.MIFFLIN_ST_JEOR.basal(input));
            if (plan.getPrescribedKcal().compareTo(basal) < 0) {
                warnings.add(("O prescrito (%s kcal) ficou abaixo do gasto basal estimado "
                        + "(%s kcal por Mifflin-St Jeor): é menos do que o corpo gasta em repouso.")
                        .formatted(number(plan.getPrescribedKcal()), number(basal)));
            }
        }
        return warnings;
    }

    /** 2 567 -> "2.567", 1,2 -> "1,2": como o profissional lê o número. */
    private static String number(BigDecimal value) {
        var symbols = new java.text.DecimalFormatSymbols(java.util.Locale.of("pt", "BR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0.#", symbols).format(value);
    }

    /**
     * A faixa de peso que mantém o adulto em eutrofia, pela altura.
     *
     * O limite inferior é 18,5 — é a dúvida que o cliente registra no
     * documento, e a resposta está na própria tabela de classificação do IMC
     * que o sistema já usa.
     */
    private EnergyDtos.HealthyWeight healthyWeight(BigDecimal heightCm) {
        HealthyWeightRange range = HealthyWeightRange.of(heightCm);
        if (range == null) {
            return null;
        }
        return new EnergyDtos.HealthyWeight(range.minimumKg(), range.maximumKg(),
                range.bmiMinimum(), range.bmiMaximum());
    }

    // ------------------------------------------------------------------ entradas

    private record Measurements(BigDecimal weightKg, BigDecimal heightCm, BigDecimal leanMassKg, int age) {}

    /**
     * Weight and height come from the assessment when one is named, and from
     * the form when it is not. The form wins over the assessment, because the
     * professional may be calculating for a weight that was not measured —
     * a target weight, for instance.
     */
    private Measurements measurements(EnergyDtos.EnergyPlanRequest request, Patient patient) {
        BigDecimal weight = request.weightKg();
        BigDecimal height = request.heightCm();
        BigDecimal lean = request.leanMassKg();

        if (request.assessmentId() != null) {
            AnthropometricAssessment assessment = assessmentRepository
                    .findById(request.assessmentId())
                    .filter(found -> found.getAccountId().equals(patient.getAccountId()))
                    .filter(found -> found.getPatientId().equals(patient.getId()))
                    .orElseThrow(() -> new NotFoundException(
                            "Avaliação antropométrica", request.assessmentId()));
            if (weight == null) weight = assessment.getWeightKg();
            if (height == null) height = assessment.getHeightCm();
            // A composição estimada pelas dobras vale mais que a do aparelho:
            // é a que a avaliação calculou com protocolo declarado.
            if (lean == null) lean = assessment.getMassLeanKg() != null
                    ? assessment.getMassLeanKg() : assessment.getBiaLeanMassKg();
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
        return new Measurements(weight, height, lean, age);
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
