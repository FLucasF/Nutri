package br.com.nutriplan.labtest.service;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.labtest.domain.LabtestClassification;
import br.com.nutriplan.labtest.domain.Labtest;
import br.com.nutriplan.labtest.domain.ReferenceRange;
import br.com.nutriplan.labtest.domain.LabtestReport;
import br.com.nutriplan.labtest.domain.LabtestParameter;
import br.com.nutriplan.labtest.domain.OrderedParameter;
import br.com.nutriplan.labtest.domain.LabtestOrder;
import br.com.nutriplan.labtest.dto.LabtestDtos;
import br.com.nutriplan.labtest.repository.LabtestRepository;
import br.com.nutriplan.labtest.repository.LabtestReportRepository;
import br.com.nutriplan.labtest.repository.LabtestParameterRepository;
import br.com.nutriplan.labtest.repository.LabtestOrderRepository;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.service.PatientService;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The patient's lab tests.
 *
 * The rule that structures this service is the usual one in this system: the
 * record keeps what was known at the moment it was made. Here that means
 * storing the reference range together with the value.
 *
 * A reference range is not a universal constant — it depends on the
 * laboratory's method and changes over time. If the classification were
 * calculated on read, correcting a range in the catalog would reclassify old
 * tests, and a result that was normal would appear abnormal without anything
 * having happened to the patient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabtestService {

    private final LabtestRepository labtestRepository;
    private final LabtestReportRepository reportRepository;
    private final LabtestParameterRepository parameterRepository;
    private final LabtestOrderRepository orderRepository;
    private final PatientService patientService;
    private final CurrentContext contextCurrent;

    // ------------------------------------------------------------- parameters

    @Transactional(readOnly = true)
    public List<LabtestDtos.ParameterResponse> parameters() {
        return parameterRepository.visibleTo(contextCurrent.accountId()).stream()
                .map(LabtestDtos.ParameterResponse::from)
                .toList();
    }

    /**
     * Registers a parameter of the practice's own.
     *
     * The system catalog covers the common lab test; the practice needs to be
     * able to add what its own practice asks for without waiting for an update.
     */
    @Transactional
    public LabtestDtos.ParameterResponse createParameter(LabtestDtos.ParameterRequest req) {
        var parameter = new LabtestParameter(contextCurrent.accountId(), req.name(),
                req.unitStandard(), req.group());

        if (req.minimum() != null || req.maximum() != null) {
            var range = new ReferenceRange();
            range.setParameter(parameter);
            range.setMinimum(req.minimum());
            range.setMaximum(req.maximum());
            parameter.getRanges().add(range);
        }
        parameterRepository.save(parameter);
        return LabtestDtos.ParameterResponse.from(parameter);
    }

    // -------------------------------------------------------------- lab tests

    @Transactional(readOnly = true)
    public List<LabtestDtos.LabtestResponse> forPatient(Long patientId) {
        patientService.accountRequire(patientId);
        return labtestRepository.forPatient(patientId, contextCurrent.accountId()).stream()
                .map(LabtestDtos.LabtestResponse::from)
                .toList();
    }

    @Transactional
    public LabtestDtos.LabtestResponse entry(Long patientId, LabtestDtos.LabtestRequest req) {
        Patient patient = patientService.accountRequire(patientId);
        LabtestParameter parameter = requireParameter(req.parameterId());

        var labtest = new Labtest(contextCurrent.accountId(), patientId, parameter, req.dateCollection());
        apply(labtest, patient, parameter, req);
        labtestRepository.save(labtest);

        log.info("Exame registrado: paciente={} parâmetro={} classificação={}",
                patientId, parameter.getName(), labtest.getClassification());
        return LabtestDtos.LabtestResponse.from(labtest);
    }

    @Transactional
    public LabtestDtos.LabtestResponse update(Long id, LabtestDtos.LabtestRequest req) {
        Labtest labtest = requireLabtest(id);
        Patient patient = patientService.accountRequire(labtest.getPatientId());
        LabtestParameter parameter = requireParameter(req.parameterId());

        labtest.setParameter(parameter);
        labtest.setDateCollection(req.dateCollection());
        apply(labtest, patient, parameter, req);
        return LabtestDtos.LabtestResponse.from(labtest);
    }

    @Transactional
    public void remove(Long id) {
        labtestRepository.delete(requireLabtest(id));
    }

    /**
     * Fills in value, unit and the classification against the range in force
     * right now.
     *
     * The range is resolved here, once, and stored. Every later read uses what
     * was stored.
     */
    private void apply(Labtest labtest, Patient patient, LabtestParameter parameter,
                         LabtestDtos.LabtestRequest req) {
        labtest.setValue(req.value());
        labtest.setUnit(StringUtils.hasText(req.unit())
                ? req.unit() : parameter.getUnitStandard());
        labtest.setNotes(req.notes());

        ReferenceRange range = rangeTo(parameter, patient);
        if (range != null) {
            labtest.setReferenceMin(range.getMinimum());
            labtest.setReferenceMax(range.getMaximum());
        } else {
            labtest.setReferenceMin(null);
            labtest.setReferenceMax(null);
        }

        // A value in a unit other than the parameter's is not classified: the
        // limits of the range are in the standard unit, and comparing numbers
        // from different scales would produce a wrong classification with the
        // appearance of a right one.
        boolean unitCompatible = labtest.getUnit().equalsIgnoreCase(parameter.getUnitStandard());
        labtest.setClassification(unitCompatible
                ? LabtestClassification.from(req.value(), labtest.getReferenceMin(), labtest.getReferenceMax())
                : null);
    }

    /** The most specific range that serves the patient. */
    private ReferenceRange rangeTo(LabtestParameter parameter, Patient patient) {
        Integer age = patient.getAge();
        return parameter.getRanges().stream()
                .filter(f -> f.serve(patient.getSex(), age))
                .max(Comparator.comparingInt(ReferenceRange::specificity))
                .orElse(null);
    }

    // ----------------------------------------------------------------- series

    @Transactional(readOnly = true)
    public LabtestDtos.SeriesResponse series(Long patientId, Long parameterId) {
        patientService.accountRequire(patientId);
        LabtestParameter parameter = requireParameter(parameterId);

        List<Labtest> labtests = labtestRepository.series(patientId, parameterId, contextCurrent.accountId());
        List<LabtestDtos.SeriesPoint> points = new ArrayList<>();
        BigDecimal previous = null;
        String firstUnit = null;
        boolean mixed = false;

        for (Labtest e : labtests) {
            if (firstUnit == null) {
                firstUnit = e.getUnit();
            } else if (!firstUnit.equalsIgnoreCase(e.getUnit())) {
                mixed = true;
            }
            // Variation only between points in the same unit: subtracting mg/dL
            // from mmol/L would give a number with no meaning at all.
            BigDecimal change = (previous != null && e.getValue() != null
                    && e.getUnit().equalsIgnoreCase(firstUnit))
                    ? e.getValue().subtract(previous) : null;
            points.add(new LabtestDtos.SeriesPoint(e.getDateCollection(), e.getValue(),
                    e.getUnit(), e.getClassification(), change));
            if (e.getValue() != null) {
                previous = e.getValue();
            }
        }

        return new LabtestDtos.SeriesResponse(parameter.getId(), parameter.getName(),
                firstUnit == null ? parameter.getUnitStandard() : firstUnit,
                points, mixed);
    }

    // ------------------------------------------------------------------- order

    @Transactional(readOnly = true)
    public List<LabtestDtos.OrderResponse> requests(Long patientId) {
        patientService.accountRequire(patientId);
        return orderRepository.forPatient(patientId, contextCurrent.accountId()).stream()
                .map(LabtestDtos.OrderResponse::from)
                .toList();
    }

    @Transactional
    public LabtestDtos.OrderResponse request(Long patientId,
                                                   LabtestDtos.OrderRequest req) {
        patientService.accountRequire(patientId);
        var order = new LabtestOrder(contextCurrent.accountId(), patientId, req.date());
        order.setNotes(req.notes());

        for (Long parameterId : req.parameterIds().stream().distinct().toList()) {
            order.getParameters().add(
                    new OrderedParameter(order, requireParameter(parameterId)));
        }
        orderRepository.save(order);
        return LabtestDtos.OrderResponse.from(order);
    }

    // ----------------------------------------------------------------- report

    @Transactional
    public void attachReport(Long id, String name, String type, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("Envie um arquivo não vazio");
        }
        if (content.length > REPORT_LIMIT) {
            throw new BusinessRuleException(
                    "O laudo pode ter no máximo %d MB".formatted(REPORT_LIMIT / (1024 * 1024)));
        }
        Labtest labtest = requireLabtest(id);
        labtest.setReportName(name);
        labtest.setReportType(type);
        // It replaces the previous one, if there is one: the key is the lab test's own id.
        reportRepository.save(new LabtestReport(labtest.getId(), content));
    }

    /** Name, type and content of the report, ready for the HTTP response. */
    public record Report(String name, String type, byte[] content) {}

    @Transactional(readOnly = true)
    public Report report(Long id) {
        Labtest labtest = requireLabtest(id);
        LabtestReport file = reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Laudo do exame", id));
        return new Report(labtest.getReportName(), labtest.getReportType(), file.getContent());
    }

    /** A practice's report is a PDF of a few hundred KB; 5 MB is room to spare. */
    private static final int REPORT_LIMIT = 5 * 1024 * 1024;

    // ------------------------------------------------------------------ apoio

    private Labtest requireLabtest(Long id) {
        return labtestRepository.accountFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Exame", id));
    }

    private LabtestParameter requireParameter(Long id) {
        return parameterRepository.visibleFind(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Parâmetro de exame", id));
    }

    /** Used by today's schedule and by the chart: what changed since the last one. */
    @Transactional(readOnly = true)
    public LocalDate lastCollection(Long patientId) {
        return labtestRepository.forPatient(patientId, contextCurrent.accountId()).stream()
                .map(Labtest::getDateCollection)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
