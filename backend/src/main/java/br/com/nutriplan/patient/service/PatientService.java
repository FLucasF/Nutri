package br.com.nutriplan.patient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nutriplan.shared.richtext.RichTextDocument;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.auth.service.AuthenticatedUser;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.dto.PatientRequest;
import br.com.nutriplan.patient.dto.PatientResponse;
import br.com.nutriplan.patient.dto.PatientSummary;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final CurrentContext contextCurrent;
    private final PatientsImporter importer;
    private final br.com.nutriplan.patient.repository.PatientTagLinkRepository tagLinkRepository;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public Page<PatientSummary> list(String term, Boolean active, Pageable pageable) {
        String search = StringUtils.hasText(term) ? term.trim() : null;
        Page<br.com.nutriplan.patient.domain.Patient> page =
                patientRepository.find(contextCurrent.accountId(), search, active, pageable);

        // As TAGs da página inteira numa consulta só. Uma por paciente daria
        // vinte e cinco consultas para desenhar uma lista.
        List<Long> ids = page.getContent().stream()
                .map(br.com.nutriplan.patient.domain.Patient::getId)
                .toList();
        Map<Long, List<String>> tagsByPatient = new HashMap<>();
        if (!ids.isEmpty()) {
            for (var link : tagLinkRepository.ofPatients(ids)) {
                tagsByPatient
                        .computeIfAbsent(link.getPatientId(), key -> new ArrayList<>())
                        .add(link.getTag().getName());
            }
        }

        return page.map(patient -> PatientSummary.from(patient,
                tagsByPatient.getOrDefault(patient.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public PatientResponse find(Long id) {
        return PatientResponse.from(accountRequire(id));
    }

    /**
     * Loads the patient already restricted to the logged-in user's account.
     * Used by the other modules too (prescription, anthropometry, schedule) so
     * that the ownership check lives in a single place.
     */
    @Transactional(readOnly = true)
    public Patient accountRequire(Long id) {
        return patientRepository.findByIdAndAccountId(id, contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException("Paciente", id));
    }

    @Transactional
    public PatientResponse create(PatientRequest req) {
        AuthenticatedUser user = contextCurrent.requireUser();
        checkPlanLimit(user);

        if (StringUtils.hasText(req.cpf())
                && patientRepository.existsByAccountIdAndCpf(user.getAccountId(), req.cpf())) {
            throw new BusinessRuleException("Já existe um paciente com este CPF neste consultório");
        }

        Patient patient = new Patient(user.getAccountId(), req.name());
        apply(req, patient);
        patientRepository.save(patient);

        log.info("Paciente criado: id={} conta={}", patient.getId(), user.getAccountId());
        return PatientResponse.from(patient);
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest req) {
        Patient patient = accountRequire(id);
        apply(req, patient);
        return PatientResponse.from(patient);
    }

    /**
     * Deactivates instead of deleting: the patient's chart, prescriptions and
     * financial transactions need to go on existing.
     */
    @Transactional
    public void deactivate(Long id) {
        Patient patient = accountRequire(id);
        patient.setActive(false);
        log.info("Paciente inativado: id={}", id);
    }

    @Transactional
    public PatientResponse reactivate(Long id) {
        Patient patient = accountRequire(id);
        checkPlanLimit(contextCurrent.requireUser());
        patient.setActive(true);
        return PatientResponse.from(patient);
    }

    /**
     * Imports patients from a spreadsheet.
     *
     * It saves one at a time instead of in a batch on purpose: one rejected row
     * — CPF already registered, plan limit reached — must not bring the others
     * down. Whoever imports two hundred patients needs to know which ones got
     * in, and not to receive "it failed" about the whole file.
     *
     * The plan limit holds here as it holds in manual registration. Importing
     * is not a shortcut around it: when the limit is reached, the import stops
     * and says which row it stopped at.
     */
    @Transactional
    public ImportResult importAll(java.io.Reader input, char separator)
            throws java.io.IOException {

        AuthenticatedUser user = contextCurrent.requireUser();
        var read = importer.read(input, separator);
        List<String> warnings = new ArrayList<>(read.warnings());
        int imported = 0;
        int ignored = read.ignored();

        for (var row : read.rows()) {
            var req = row.patient();
            try {
                checkPlanLimit(user);
            } catch (BusinessRuleException e) {
                warnings.add("Importação interrompida na linha %d: %s"
                        .formatted(row.number(), e.getMessage()));
                ignored += read.rows().size() - imported - ignored;
                break;
            }

            if (StringUtils.hasText(req.cpf())
                    && patientRepository.existsByAccountIdAndCpf(user.getAccountId(), req.cpf())) {
                ignored++;
                if (warnings.size() < 25) {
                    warnings.add("Linha %d ignorada: já existe paciente com o CPF %s."
                            .formatted(row.number(), req.cpf()));
                }
                continue;
            }

            Patient patient = new Patient(user.getAccountId(), req.name());
            apply(req, patient);
            patientRepository.save(patient);
            imported++;
        }

        log.info("Pacientes importados: {} de {} linhas, conta={}",
                imported, read.rows().size(), user.getAccountId());
        return new ImportResult(imported, ignored, warnings);
    }

    public record ImportResult(int imported, int ignored, List<String> warnings) {}

    private void checkPlanLimit(AuthenticatedUser user) {
        var plan = user.getPlan();
        if (plan.unlimitedPatients()) {
            return;
        }
        long active = patientRepository.countByAccountIdAndActiveTrue(user.getAccountId());
        if (active >= plan.maxPatients()) {
            throw new BusinessRuleException(
                    "O plano %s permite no máximo %d pacientes ativos. Faça upgrade para cadastrar mais."
                            .formatted(plan.name(), plan.maxPatients()));
        }
    }

    private void apply(PatientRequest req, Patient patient) {
        patient.setName(req.name());
        patient.setEmail(req.email());
        patient.setPhone(req.phone());
        patient.setDateBirth(req.dateBirth());
        patient.setSex(req.sex());
        patient.setCpf(req.cpf());
        patient.setNickname(req.nickname());
        // A condição biológica só se aplica a mulher. Guardá-la para um homem
        // seria registrar um fato que não existe.
        patient.setBiologicalCondition(
                req.sex() == br.com.nutriplan.patient.domain.Sex.FEMALE
                        ? req.biologicalCondition()
                        : null);
        patient.setOccupation(req.occupation());
        patient.setGoal(req.goal());
        patient.setNotes(RichTextDocument.ofTextOrDocument(req.notes(), mapper).json());
    }
}
