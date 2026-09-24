package br.com.nutriplan.prescription.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.BiologicalCondition;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.prescription.domain.DriReference;
import br.com.nutriplan.prescription.domain.MealPlan;
import br.com.nutriplan.prescription.dto.PrescriptionDtos;
import br.com.nutriplan.prescription.repository.MealPlanRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;

/**
 * O cardápio contra as DRI: quanto de cada micronutriente o dia entrega, e
 * quanto isso é da referência do paciente.
 *
 * A leitura é a das barras de adequação que o cliente conhece do WebDiet: de
 * 80 a 120% da referência está adequado; abaixo, falta; acima, sobra. O sódio
 * se lê ao contrário — a referência é um limite, e o que importa é não
 * passar dele.
 *
 * Um total que só parte dos alimentos informou sai marcado como incompleto:
 * o número existe, mas subestima. Um nutriente que nenhum alimento informou
 * não vira zero — vira "sem dado", porque zero afirmaria uma ausência.
 */
@Service
@RequiredArgsConstructor
public class AdequacyService {

    private static final BigDecimal LOW = BigDecimal.valueOf(80);
    private static final BigDecimal HIGH = BigDecimal.valueOf(120);

    private final MealPlanRepository planRepository;
    private final PatientRepository patientRepository;
    private final MealPlanService mealPlanService;
    private final NutritionalCalculator calculator;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public PrescriptionDtos.AdequacyResponse adequacy(Long planId) {
        Long accountId = currentContext.accountId();
        MealPlan plan = planRepository.loadComplete(planId, accountId)
                .orElseThrow(() -> new NotFoundException("Plano", planId));

        if (plan.getPatientId() == null) {
            return unavailable("Modelo sem paciente: as referências dependem do sexo e da idade de quem vai comer.");
        }
        Patient patient = patientRepository.findByIdAndAccountId(plan.getPatientId(), accountId).orElse(null);
        if (patient == null) {
            return unavailable("O paciente deste plano não foi encontrado.");
        }
        if (patient.getSex() == null) {
            return unavailable("Informe o sexo do paciente no cadastro: as referências são diferentes para cada um.");
        }
        LocalDate reference = plan.getValidityStart() != null ? plan.getValidityStart() : LocalDate.now();
        Integer age = patient.ageAt(reference);
        if (age == null) {
            return unavailable("Informe a data de nascimento do paciente: as referências mudam com a idade.");
        }
        if (age < 1) {
            return unavailable("Menores de um ano têm referências próprias, que não estão no sistema.");
        }
        if (patient.getBiologicalCondition() == BiologicalCondition.PREGNANT
                || patient.getBiologicalCondition() == BiologicalCondition.LACTATING) {
            return unavailable("Gestantes e lactantes têm referências próprias, que não estão no sistema. "
                    + "Compare os valores do dia com as DRI da condição.");
        }

        var foods = mealPlanService.loadFoodsOfMeals(plan.getMeals(), accountId);
        var total = calculator.totalMeals(plan.getMeals(), foods);
        Set<String> incomplete = total.nutrientsIncomplete();

        List<PrescriptionDtos.AdequacyRow> rows = new ArrayList<>();
        for (DriReference.Target target : DriReference.forPerson(patient.getSex(), age)) {
            BigDecimal intake = total.coverage().getOrDefault(target.nutrient(), 0) == 0
                    ? null
                    : total.composition().valueDe(target.nutrient());
            BigDecimal referenceValue = BigDecimal.valueOf(target.value());
            BigDecimal percent = intake == null ? null
                    : intake.multiply(BigDecimal.valueOf(100))
                            .divide(referenceValue, 0, RoundingMode.HALF_UP);
            rows.add(new PrescriptionDtos.AdequacyRow(
                    target.nutrient(), target.label(), target.unit(),
                    intake == null ? null : intake.setScale(2, RoundingMode.HALF_UP),
                    referenceValue, target.kind(), target.kind().getDescription(),
                    percent, status(target.kind(), percent),
                    intake != null && incomplete.contains(target.nutrient())));
        }

        String who = (patient.getSex() == br.com.nutriplan.patient.domain.Sex.MALE ? "Homem" : "Mulher")
                + ", " + DriReference.stageDescription(age);
        return new PrescriptionDtos.AdequacyResponse(true, null, who, age, rows);
    }

    private static PrescriptionDtos.AdequacyStatus status(DriReference.Kind kind, BigDecimal percent) {
        if (percent == null) {
            return PrescriptionDtos.AdequacyStatus.NO_DATA;
        }
        if (kind == DriReference.Kind.LIMIT) {
            return percent.compareTo(BigDecimal.valueOf(100)) > 0
                    ? PrescriptionDtos.AdequacyStatus.ABOVE_LIMIT
                    : PrescriptionDtos.AdequacyStatus.WITHIN_LIMIT;
        }
        if (percent.compareTo(LOW) < 0) {
            return PrescriptionDtos.AdequacyStatus.BELOW;
        }
        if (percent.compareTo(HIGH) > 0) {
            return PrescriptionDtos.AdequacyStatus.ABOVE;
        }
        return PrescriptionDtos.AdequacyStatus.ADEQUATE;
    }

    private static PrescriptionDtos.AdequacyResponse unavailable(String reason) {
        return new PrescriptionDtos.AdequacyResponse(false, reason, null, null, List.of());
    }
}
