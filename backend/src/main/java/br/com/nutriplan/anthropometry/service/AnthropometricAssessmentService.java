package br.com.nutriplan.anthropometry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.anthropometry.domain.AnthropometricAssessment;
import br.com.nutriplan.anthropometry.domain.BmiClassification;
import br.com.nutriplan.anthropometry.domain.Skinfold;
import br.com.nutriplan.anthropometry.domain.EnergyExpenditureEquation;
import br.com.nutriplan.anthropometry.domain.CompositionProtocol;
import br.com.nutriplan.anthropometry.domain.AssessmentCircumference;
import br.com.nutriplan.anthropometry.domain.CardiometabolicRisk;
import br.com.nutriplan.anthropometry.domain.CircumferenceSite;
import br.com.nutriplan.anthropometry.domain.Side;
import br.com.nutriplan.anthropometry.domain.ChildClassification;
import br.com.nutriplan.anthropometry.domain.GestationalGain;
import br.com.nutriplan.anthropometry.domain.GrowthIndicator;
import br.com.nutriplan.anthropometry.domain.GainStatus;
import br.com.nutriplan.anthropometry.repository.GrowthChartRepository;
import br.com.nutriplan.patient.domain.Sex;
import br.com.nutriplan.anthropometry.dto.AnthropometryDtos;
import br.com.nutriplan.anthropometry.repository.AnthropometricAssessmentRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnthropometricAssessmentService {

    private static final int SCALE = 2;

    private final AnthropometricAssessmentRepository assessmentRepository;
    private final PatientRepository patientRepository;
    private final GrowthChartRepository chartRepository;
    private final ZScoreCalculator scoreZ;
    private final CurrentContext contextCurrent;
    private final AnthropometryReportGenerator reportGenerator;
    private final ObjectMapper mapper;

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<AnthropometryDtos.AssessmentResponse> patientList(Long patientId) {
        Long accountId = contextCurrent.accountId();
        Patient patient = requirePatient(patientId, accountId);

        return assessmentRepository
                .findByAccountIdAndPatientIdOrderByDateAscIdAsc(accountId, patientId)
                .stream()
                .map(a -> buildAnswer(a, patient))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnthropometryDtos.AssessmentResponse detail(Long id) {
        AnthropometricAssessment assessment = accountRequire(id);
        return buildAnswer(assessment, requirePatient(assessment.getPatientId(), assessment.getAccountId()));
    }

    @Transactional(readOnly = true)
    public AnthropometricAssessment accountRequire(Long id) {
        return assessmentRepository.findByIdAndAccountId(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Avaliação antropométrica", id));
    }

    // ------------------------------------------------------------------ writing

    @Transactional
    public AnthropometryDtos.AssessmentResponse create(Long patientId,
                                                     AnthropometryDtos.AssessmentRequest req) {
        Long accountId = contextCurrent.accountId();
        Patient patient = requirePatient(patientId, accountId);

        var assessment = new AnthropometricAssessment(accountId, patientId, req.date());
        apply(req, assessment, patient);
        assessmentRepository.save(assessment);

        log.info("Avaliação registrada: id={} paciente={} conta={}",
                assessment.getId(), patientId, accountId);
        return buildAnswer(assessment, patient);
    }

    @Transactional
    public AnthropometryDtos.AssessmentResponse update(Long id,
                                                         AnthropometryDtos.AssessmentRequest req) {
        AnthropometricAssessment assessment = accountRequire(id);
        Patient patient = requirePatient(assessment.getPatientId(), assessment.getAccountId());

        assessment.setDate(req.date());
        apply(req, assessment, patient);
        return buildAnswer(assessment, patient);
    }

    @Transactional
    public void remove(Long id) {
        assessmentRepository.delete(accountRequire(id));
        log.info("Avaliação removida: id={}", id);
    }

    // -------------------------------------------------------------- calculation

    private void apply(AnthropometryDtos.AssessmentRequest req,
                         AnthropometricAssessment assessment,
                         Patient patient) {

        assessment.setWeightKg(req.weightKg());
        assessment.setHeightCm(req.heightCm());
        assessment.setNotes(RichTextDocument.ofTextOrDocument(req.notes(), mapper).json());
        assessment.setGestationalWeek(req.gestationalWeek());
        assessment.setWeightGestationalPreKg(req.weightGestationalPreKg());

        assessment.setHeightSittingCm(req.heightSittingCm());
        assessment.setHeightKneeCm(req.heightKneeCm());
        assessment.setDiameterHumerus(req.diameterHumerus());
        assessment.setDiameterWrist(req.diameterWrist());
        assessment.setDiameterFemur(req.diameterFemur());

        // A bioimpedância é copiada como veio do aparelho. Nada aqui recalcula
        // esses números: substituir medida por estimativa e continuar chamando
        // de medida é o tipo de coisa que ninguém percebe depois.
        assessment.setBiaFatPercentage(req.biaFatPercentage());
        assessment.setBiaFatMassKg(req.biaFatMassKg());
        assessment.setBiaMusclePercentage(req.biaMusclePercentage());
        assessment.setBiaMuscleMassKg(req.biaMuscleMassKg());
        assessment.setBiaLeanMassKg(req.biaLeanMassKg());
        assessment.setBiaBoneMassKg(req.biaBoneMassKg());
        assessment.setBiaVisceralFat(req.biaVisceralFat());
        assessment.setBiaBodyWaterPercentage(req.biaBodyWaterPercentage());
        assessment.setBiaMetabolicAge(req.biaMetabolicAge());

        applySkinfolds(req.skinfolds(), assessment);
        applyCircumferences(req.circumferences(), assessment);

        // It recalculates from scratch: editing one skinfold without
        // recalculating would leave the stored percentage inconsistent with the
        // measurements next to it.
        assessment.clearComposition();
        assessment.clearExpenditureEnergy();

        if (req.protocolComposition() != null) {
            estimateComposition(req.protocolComposition(), assessment, patient);
        }
        if (req.equationExpenditure() != null) {
            estimateExpenditureEnergy(req.equationExpenditure(), req.factorActivity(), assessment, patient);
        }
    }

    /**
     * Estimates body composition by the chosen protocol.
     *
     * It refuses when any required skinfold is missing, naming which ones —
     * completing the calculation with a missing skinfold would invent body
     * composition.
     */
    private void estimateComposition(CompositionProtocol protocol,
                                   AnthropometricAssessment assessment,
                                   Patient patient) {

        if (protocol.requiresSex() && patient.getSex() == null) {
            throw new BusinessRuleException(
                    "O protocolo %s depende do sexo do paciente, que não está informado no cadastro."
                            .formatted(protocol.getDescription()));
        }
        Integer age = patient.ageAt(assessment.getDate());
        if (protocol.requiresAge() && age == null) {
            throw new BusinessRuleException(
                    "O protocolo %s depende da idade, e o paciente não tem data de nascimento cadastrada."
                            .formatted(protocol.getDescription()));
        }

        Map<Skinfold, Double> skinfolds = assessment.skinfoldsMeasures();
        List<Skinfold> missing = protocol.skinfoldsMissing(skinfolds, patient.getSex());
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    "O protocolo %s exige as dobras que faltam: %s.".formatted(
                            protocol.getDescription(),
                            missing.stream().map(Skinfold::getDescription).collect(
                                    java.util.stream.Collectors.joining(", "))));
        }

        double percentage = protocol.fatPercentage(skinfolds, patient.getSex(), age);
        if (percentage <= 0 || percentage >= 100) {
            throw new BusinessRuleException(
                    "As dobras informadas produzem um percentual de gordura fora da faixa possível. "
                    + "Confira as medidas.");
        }

        BigDecimal percentageArredondado = round(percentage);
        assessment.setProtocolComposition(protocol);
        assessment.setPercentageFat(percentageArredondado);

        // Fat and lean mass depend on the weight; without it only the percentage is left.
        if (assessment.getWeightKg() != null) {
            BigDecimal massFat = assessment.getWeightKg()
                    .multiply(percentageArredondado)
                    .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
            assessment.setMassFatKg(massFat);
            assessment.setMassLeanKg(
                    assessment.getWeightKg().subtract(massFat).setScale(SCALE, RoundingMode.HALF_UP));
        }
    }

    private void estimateExpenditureEnergy(EnergyExpenditureEquation equation,
                                        BigDecimal factorActivity,
                                        AnthropometricAssessment assessment,
                                        Patient patient) {

        if (assessment.getWeightKg() == null || assessment.getHeightCm() == null) {
            throw new BusinessRuleException(
                    "A estimativa de gasto energético depende de peso e altura.");
        }
        if (patient.getSex() == null) {
            throw new BusinessRuleException(
                    "A estimativa de gasto energético depende do sexo do paciente.");
        }
        Integer age = patient.ageAt(assessment.getDate());
        if (age == null) {
            throw new BusinessRuleException(
                    "A equação de gasto energético depende da idade, e o paciente não tem "
                    + "data de nascimento cadastrada.");
        }

        double basal = equation.basal(
                assessment.getWeightKg().doubleValue(),
                assessment.getHeightCm().doubleValue(),
                patient.getSex(),
                age);

        assessment.setEquationExpenditure(equation);
        assessment.setBasalExpenditureKcal(round(basal));

        if (factorActivity != null) {
            assessment.setFactorActivity(factorActivity);
            assessment.setTotalExpenditureKcal(
                    round(basal * factorActivity.doubleValue()));
        }
    }

    // ---------------------------------------------------------------- progress

    /**
     * The patient's series with the variations between assessments.
     *
     * Two rules of honesty govern the comparison: a measurement absent at
     * either end produces no variation — absence is not a reduction; and a fat
     * percentage estimated by different protocols is not compared, because each
     * protocol has its own standard error and the difference between them would
     * be read as a change in the patient.
     */
    /**
     * O relatorio de evolucao em PDF, com os graficos e as tabelas comparativas.
     *
     * Vive no servico e nao no controlador porque as circunferencias sao uma
     * colecao preguicosa: montar a folha fora da transacao falharia na
     * primeira avaliacao, com um erro que nao menciona PDF nenhum.
     */
    @Transactional(readOnly = true)
    public Report report(Long patientId) {
        Long accountId = contextCurrent.accountId();
        Patient patient = requirePatient(patientId, accountId);
        List<AnthropometricAssessment> series =
                assessmentRepository.findByAccountIdAndPatientIdOrderByDateAscIdAsc(
                        accountId, patientId);
        return new Report(patient.getName(), reportGenerator.generate(patient.getName(), series));
    }

    public record Report(String patientName, byte[] content) {}

    @Transactional(readOnly = true)
    public AnthropometryDtos.ProgressResponse progress(Long patientId) {
        Long accountId = contextCurrent.accountId();
        Patient patient = requirePatient(patientId, accountId);

        List<AnthropometricAssessment> series =
                assessmentRepository.findByAccountIdAndPatientIdOrderByDateAscIdAsc(accountId, patientId);

        List<AnthropometryDtos.ProgressPoint> points = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            AnthropometricAssessment current = series.get(i);
            AnthropometricAssessment previous = i > 0 ? series.get(i - 1) : null;
            AnthropometricAssessment first = series.get(0);

            points.add(new AnthropometryDtos.ProgressPoint(
                    current.getId(),
                    current.getDate(),
                    current.getWeightKg(),
                    current.getBmi(),
                    current.getPercentageFat(),
                    current.getProtocolComposition(),
                    previous == null ? List.of() : compareCom(current, previous),
                    i == 0 ? List.of() : compareCom(current, first)));
        }

        return new AnthropometryDtos.ProgressResponse(
                patientId, patient.getName(), series.size(), points);
    }

    private List<AnthropometryDtos.Change> compareCom(AnthropometricAssessment current,
                                                          AnthropometricAssessment reference) {
        List<AnthropometryDtos.Change> changes = new ArrayList<>();

        changes.add(changeSimple("weightKg", "Peso (kg)",
                current.getWeightKg(), reference.getWeightKg()));
        changes.add(changeSimple("bmi", "IMC",
                current.getBmi(), reference.getBmi()));
        changes.add(changeSimple("circumferenceWaist", "Cintura (cm)",
                current.circumference(CircumferenceSite.WAIST, Side.SINGLE),
                reference.circumference(CircumferenceSite.WAIST, Side.SINGLE)));
        changes.add(changeSimple("circumferenceHip", "Quadril (cm)",
                current.circumference(CircumferenceSite.HIP, Side.SINGLE),
                reference.circumference(CircumferenceSite.HIP, Side.SINGLE)));
        changes.add(changeSimple("massLeanKg", "Massa magra (kg)",
                current.getMassLeanKg(), reference.getMassLeanKg()));

        changes.add(compositionChange(current, reference));

        return changes;
    }

    private AnthropometryDtos.Change changeSimple(String measure, String label,
                                                        BigDecimal current, BigDecimal reference) {
        if (current == null || reference == null) {
            return new AnthropometryDtos.Change(measure, label, current, reference, null, false,
                    "Medida ausente em uma das avaliações.");
        }
        return new AnthropometryDtos.Change(measure, label, current, reference,
                current.subtract(reference).setScale(SCALE, RoundingMode.HALF_UP), true, null);
    }

    private AnthropometryDtos.Change compositionChange(AnthropometricAssessment current,
                                                             AnthropometricAssessment reference) {
        final String measure = "percentageFat";
        final String label = "Gordura corporal (%)";

        if (!current.hasEstimatedComposition() || !reference.hasEstimatedComposition()) {
            return new AnthropometryDtos.Change(measure, label,
                    current.getPercentageFat(), reference.getPercentageFat(), null, false,
                    "Composição não estimada em uma das avaliações.");
        }
        if (current.getProtocolComposition() != reference.getProtocolComposition()) {
            return new AnthropometryDtos.Change(measure, label,
                    current.getPercentageFat(), reference.getPercentageFat(), null, false,
                    "Protocolos diferentes (%s e %s). Cada protocolo tem erro-padrão próprio, "
                            .formatted(reference.getProtocolComposition().getDescription(),
                                    current.getProtocolComposition().getDescription())
                            + "e a diferença entre eles não representa mudança do paciente.");
        }
        return new AnthropometryDtos.Change(measure, label,
                current.getPercentageFat(), reference.getPercentageFat(),
                current.getPercentageFat().subtract(reference.getPercentageFat())
                        .setScale(SCALE, RoundingMode.HALF_UP),
                true, null);
    }

    // ------------------------------------------------------------------ apoio

    private AnthropometryDtos.AssessmentResponse buildAnswer(AnthropometricAssessment a,
                                                                Patient patient) {
        Integer age = patient.ageAt(a.getDate());
        BigDecimal bmi = a.getBmi();
        BigDecimal rcq = a.getRatioWaistHip();

        return new AnthropometryDtos.AssessmentResponse(
                a.getId(), a.getPatientId(), patient.getName(), a.getDate(),
                a.getWeightKg(), a.getHeightCm(), age,
                skinfoldsAsMap(a), circumferencesOf(a),
                a.getDiameterHumerus(), a.getDiameterWrist(), a.getDiameterFemur(),
                a.getHeightSittingCm(), a.getHeightKneeCm(),
                a.getBiaFatPercentage(), a.getBiaFatMassKg(), a.getBiaMusclePercentage(),
                a.getBiaMuscleMassKg(), a.getBiaLeanMassKg(), a.getBiaBoneMassKg(),
                a.getBiaVisceralFat(), a.getBiaBodyWaterPercentage(), a.getBiaMetabolicAge(),
                bmi, classify(bmi, age),
                rcq, classifyRisk(rcq, patient),
                a.getProtocolComposition() == null ? null
                        : new AnthropometryDtos.CompositionBodyResponse(
                                a.getProtocolComposition(),
                                a.getProtocolComposition().getDescription(),
                                a.getPercentageFat(), a.getMassFatKg(), a.getMassLeanKg()),
                a.getEquationExpenditure() == null ? null
                        : new AnthropometryDtos.ExpenditureEnergyResponse(
                                a.getEquationExpenditure(), a.getEquationExpenditure().getDescription(),
                                a.getFactorActivity(), a.getBasalExpenditureKcal(), a.getTotalExpenditureKcal()),
                childGrowth(a, patient),
                pregnancy(a),
                a.getNotes(), a.getCreatedAt());
    }

    /**
     * Child reading by the WHO curves.
     *
     * Up to 19 years BMI is not read by the adult band: the same measurement
     * means different things depending on age, and the correct comparison is
     * against the distribution for that age. Above that the answer comes back
     * empty, with the reason — and the adult classification is the one that
     * holds.
     */
    private AnthropometryDtos.Derived<AnthropometryDtos.ChildGrowthResponse>
            childGrowth(AnthropometricAssessment a, Patient patient) {

        if (patient.getDateBirth() == null) {
            return AnthropometryDtos.Derived.missing(
                    "A curva depende da idade em meses. Cadastre a data de nascimento.");
        }
        if (patient.getSex() == null) {
            return AnthropometryDtos.Derived.missing(
                    "As curvas da OMS são especificas por sexo. Informe o sexo do paciente.");
        }

        int months = (int) java.time.temporal.ChronoUnit.MONTHS.between(
                patient.getDateBirth(), a.getDate());
        if (!ZScoreCalculator.ageHasChart(months)) {
            return AnthropometryDtos.Derived.missing(
                    "As curvas da OMS vao até 19 anos. Acima disso vale a classificação adulta.");
        }

        var indicators = new java.util.ArrayList<AnthropometryDtos.ChildIndicatorResponse>();
        addIndicator(indicators, GrowthIndicator.BMI_TO_AGE,
                patient.getSex(), months, a.getBmi());
        addIndicator(indicators, GrowthIndicator.HEIGHT_TO_AGE,
                patient.getSex(), months, a.getHeightCm());

        if (indicators.isEmpty()) {
            return AnthropometryDtos.Derived.missing(
                    "As curvas de crescimento não estao carregadas nesta instalacao.");
        }
        return AnthropometryDtos.Derived.from(
                new AnthropometryDtos.ChildGrowthResponse(months, indicators));
    }

    private void addIndicator(
            java.util.List<AnthropometryDtos.ChildIndicatorResponse> destination,
            GrowthIndicator indicator, Sex sex, int months, BigDecimal value) {

        if (value == null) {
            return;
        }
        var chart = chartRepository.findByIndicatorAndSexAndMonth(indicator, sex, months)
                .orElse(null);
        if (chart == null) {
            return;
        }
        BigDecimal z = scoreZ.calculate(chart, value);
        var classification = ChildClassification.from(indicator, z, months);
        destination.add(new AnthropometryDtos.ChildIndicatorResponse(
                indicator, indicator.getDescription(), z, classification,
                classification == null ? null : classification.getDescription(),
                classification != null && classification.requiresAttention(),
                chart.reference()));
    }

    /**
     * Weight gain in pregnancy, by the IOM 2009 bands.
     *
     * Without the pre-pregnancy weight the system does not classify. It could
     * estimate it from the current BMI, but the current BMI already includes
     * the gain being assessed — the calculation would bite itself.
     */
    private AnthropometryDtos.Derived<AnthropometryDtos.PregnancyResponse> pregnancy(
            AnthropometricAssessment a) {

        if (!a.isGestational()) {
            return null;
        }
        if (a.getWeightGestationalPreKg() == null) {
            return AnthropometryDtos.Derived.missing(
                    "A faixa de ganho depende do IMC anterior a gestação. "
                            + "Informe o peso pre-gestacional.");
        }
        if (a.getHeightCm() == null || a.getHeightCm().signum() <= 0) {
            return AnthropometryDtos.Derived.missing(
                    "O IMC pre-gestacional depende da altura.");
        }

        BigDecimal heightM = a.getHeightCm().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal bmiPre = a.getWeightGestationalPreKg()
                .divide(heightM.multiply(heightM), SCALE, RoundingMode.HALF_UP);

        var range = GestationalGain.byBmiGestationalPre(bmiPre);
        if (range == null) {
            return AnthropometryDtos.Derived.missing(
                    "Não foi possível enquadrar o IMC pre-gestacional numa faixa.");
        }

        int week = a.getGestationalWeek();
        BigDecimal gain = a.getWeightKg().subtract(a.getWeightGestationalPreKg())
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal min = range.expectedMin(week);
        BigDecimal max = range.expectedMax(week);
        var status = GainStatus.from(gain, min, max);

        return AnthropometryDtos.Derived.from(new AnthropometryDtos.PregnancyResponse(
                week, a.getWeightGestationalPreKg(), bmiPre,
                range, range.getDescription(), gain, min, max,
                status, status == null ? null : status.getDescription(),
                range.getTotalGainMin(), range.getTotalGainMax()));
    }

    private AnthropometryDtos.Derived<BmiClassification> classify(BigDecimal bmi, Integer age) {
        if (bmi == null) {
            return AnthropometryDtos.Derived.missing("Informe peso e altura para calcular o IMC.");
        }
        if (age != null && age < BmiClassification.MINIMUM_AGE_ADULT) {
            return AnthropometryDtos.Derived.missing(
                    "As faixas da OMS valem para adultos. Abaixo de %d anos a leitura correta é "
                            .formatted(BmiClassification.MINIMUM_AGE_ADULT)
                            + "por percentil de idade e sexo.");
        }
        return AnthropometryDtos.Derived.from(BmiClassification.toAdult(bmi, age));
    }

    private AnthropometryDtos.Derived<CardiometabolicRisk> classifyRisk(BigDecimal rcq,
                                                                                Patient patient) {
        if (rcq == null) {
            return AnthropometryDtos.Derived.missing(
                    "Informe cintura e quadril para calcular a relação.");
        }
        if (patient.getSex() == null) {
            return AnthropometryDtos.Derived.missing(
                    "Os pontos de corte são específicos por sexo, que não está informado no cadastro.");
        }
        return AnthropometryDtos.Derived.from(
                CardiometabolicRisk.byRatioWaistHip(rcq, patient.getSex()));
    }

    private void applySkinfolds(Map<String, BigDecimal> skinfolds, AnthropometricAssessment assessment) {
        for (Skinfold skinfold : Skinfold.values()) {
            assessment.defineSkinfold(skinfold, null);
        }
        if (skinfolds == null) {
            return;
        }
        skinfolds.forEach((key, value) -> {
            Skinfold skinfold = skinfoldByKey(key);
            if (skinfold != null && value != null && value.signum() > 0) {
                assessment.defineSkinfold(skinfold, value);
            }
        });
    }

    private Skinfold skinfoldByKey(String key) {
        for (Skinfold skinfold : Skinfold.values()) {
            if (skinfold.name().equalsIgnoreCase(key)) {
                return skinfold;
            }
        }
        return null;
    }

    /**
     * Rewrites the assessment's circumferences from the request.
     *
     * The whole set is replaced rather than merged: a measurement that left the
     * form left because it was not taken this time, and keeping the old value
     * would attribute today's date to last month's tape measure.
     */
    private void applyCircumferences(List<AnthropometryDtos.CircumferenceValue> measures,
                                     AnthropometricAssessment a) {
        if (measures == null) {
            a.clearCircumferences();
            return;
        }
        var seen = new java.util.HashSet<String>();
        for (var measure : measures) {
            if (measure == null || measure.valueCm() == null || measure.valueCm().signum() <= 0) {
                continue;
            }
            Side side = measure.side() == null ? Side.SINGLE : measure.side();
            if (!measure.site().isBilateral() && side != Side.SINGLE) {
                throw new BusinessRuleException(
                        measure.site().getDescription() + " não é medida por lado.");
            }
            if (!seen.add(measure.site().name() + "|" + side)) {
                throw new BusinessRuleException(
                        "A mesma circunferência chegou duas vezes: "
                                + measure.site().getDescription() + ".");
            }
            a.set(measure.site(), side, measure.valueCm());
        }
        // O que nao veio no pedido some — mas por remocao do que sobrou, e nao
        // apagando tudo antes de reescrever.
        a.keepOnly(seen);
    }

    private Map<String, BigDecimal> skinfoldsAsMap(AnthropometricAssessment a) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        a.skinfoldsMeasures().forEach((skinfold, value) ->
                map.put(skinfold.name(), BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP)));
        return map;
    }

    /** Only the circumferences actually measured enter the response. */
    private List<AnthropometryDtos.CircumferenceValue> circumferencesOf(AnthropometricAssessment a) {
        return a.getCircumferences().stream()
                .sorted(java.util.Comparator
                        .comparing((AssessmentCircumference m) -> m.getSite().ordinal())
                        .thenComparing(m -> m.getSide().ordinal()))
                .map(m -> new AnthropometryDtos.CircumferenceValue(
                        m.getSite(), m.getSide(), m.getValueCm()))
                .toList();
    }

    private Patient requirePatient(Long patientId, Long accountId) {
        return patientRepository.findByIdAndAccountId(patientId, accountId)
                .orElseThrow(() -> new NotFoundException("Paciente", patientId));
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Maximum date accepted for an assessment. It exists so the test can be explicit. */
    public static LocalDate maximumDateAllowed() {
        return LocalDate.now();
    }
}
