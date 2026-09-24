package br.com.nutriplan.prescription.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.patient.domain.Patient;
import br.com.nutriplan.patient.repository.PatientRepository;
import br.com.nutriplan.prescription.domain.MealPlan;
import br.com.nutriplan.prescription.repository.MealPlanRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;

/**
 * O segundo fator do link do paciente: a data de nascimento.
 *
 * O link do plano sempre foi autorizado pela posse do identificador. O cliente
 * usa o WebDiet, onde o paciente confirma a data de nascimento antes de ver o
 * plano, e pediu o mesmo: um link encaminhado por engano não abre sozinho.
 *
 * Confirmada a data, o servidor devolve um passe assinado, válido por doze
 * horas, que a página manda nas leituras seguintes — no cabeçalho para o
 * plano e no endereço para as figuras, que o navegador busca sem cabeçalho.
 * O passe não carrega a data: é um HMAC do identificador e da validade.
 *
 * Três regras:
 *
 *   - paciente sem data de nascimento cadastrada não tem o que confirmar, e
 *     o link abre como antes;
 *   - o dono da conta, logado, abre o plano sem digitar nada — é quem o
 *     escreveu, e confere o que o paciente vai ver;
 *   - cinco datas erradas seguidas travam o link por quinze minutos. São 36
 *     mil datas possíveis em cem anos; sem a trava, adivinhar seria questão
 *     de paciência.
 */
@Service
public class PlanAccessService {

    static final String HEADER = "X-Plan-Access";
    private static final long VALIDITY_SECONDS = 12 * 3600;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_SECONDS = 15 * 60;

    private final MealPlanRepository planRepository;
    private final PatientRepository patientRepository;
    private final CurrentContext currentContext;
    private final byte[] secret;
    private final Clock clock;

    /** Falhas por identificador: quantas seguidas e até quando o link fica travado. */
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    private record Attempts(int failures, Instant lockedUntil) {}

    public PlanAccessService(MealPlanRepository planRepository,
                             PatientRepository patientRepository,
                             CurrentContext currentContext,
                             @Value("${nutriplan.jwt.secret}") String secret) {
        this.planRepository = planRepository;
        this.patientRepository = patientRepository;
        this.currentContext = currentContext;
        this.secret = ("plan-access:" + secret).getBytes(StandardCharsets.UTF_8);
        this.clock = Clock.systemUTC();
    }

    /** O paciente do plano tem data de nascimento para confirmar. */
    @Transactional(readOnly = true)
    public boolean requiresBirthDate(MealPlan plan) {
        return birthDate(plan) != null;
    }

    /** Quem pede é o consultório dono do plano, logado. */
    public boolean ownerViewing(MealPlan plan) {
        return currentContext.user()
                .map(user -> user.getAccountId().equals(plan.getAccountId()))
                .orElse(false);
    }

    /** A leitura pode seguir: sem data a confirmar, o dono logado, ou um passe válido. */
    @Transactional(readOnly = true)
    public boolean allowed(MealPlan plan, String token) {
        return !requiresBirthDate(plan) || ownerViewing(plan) || valid(plan.getPublicIdentifier(), token);
    }

    /**
     * Confere a data e devolve o passe.
     *
     * A resposta a uma data errada não diz se o plano existe com outra data:
     * diz só que não confere, e quantas tentativas restam.
     */
    @Transactional(readOnly = true)
    public String confirm(String identifier, LocalDate birthDate) {
        MealPlan plan = planRepository.findByPublicIdentifier(identifier)
                .filter(MealPlan::isVisibleByLink)
                .orElseThrow(() -> new NotFoundException("Plano não encontrado. Confira o link recebido."));

        Instant now = clock.instant();
        Attempts current = attempts.get(identifier);
        if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
            long minutes = Math.max(1, (current.lockedUntil().getEpochSecond() - now.getEpochSecond() + 59) / 60);
            throw new BusinessRuleException(
                    "Muitas tentativas com a data errada. Tente de novo em " + minutes + " minutos.");
        }

        LocalDate expected = birthDate(plan);
        if (expected == null) {
            // Nada a confirmar: o passe sai do mesmo jeito, e a página segue.
            return token(identifier);
        }
        if (birthDate == null || !birthDate.equals(expected)) {
            int failures = (current == null || current.lockedUntil() != null ? 0 : current.failures()) + 1;
            if (failures >= MAX_FAILURES) {
                attempts.put(identifier, new Attempts(0, now.plusSeconds(LOCK_SECONDS)));
                throw new BusinessRuleException(
                        "A data de nascimento não confere. Por segurança, o link fica travado por 15 minutos.");
            }
            attempts.put(identifier, new Attempts(failures, null));
            int left = MAX_FAILURES - failures;
            throw new BusinessRuleException(
                    "A data de nascimento não confere com o cadastro. "
                            + (left == 1 ? "Resta 1 tentativa." : "Restam " + left + " tentativas."));
        }
        attempts.remove(identifier);
        return token(identifier);
    }

    /** Um passe novo, para o dono logado ou depois da data confirmada. */
    public String token(String identifier) {
        long expiry = clock.instant().getEpochSecond() + VALIDITY_SECONDS;
        return expiry + "." + sign(identifier + "|" + expiry);
    }

    public boolean valid(String identifier, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiry;
        try {
            expiry = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (clock.instant().getEpochSecond() > expiry) {
            return false;
        }
        byte[] expected = sign(identifier + "|" + expiry).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, given);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o passe do plano.", e);
        }
    }

    private LocalDate birthDate(MealPlan plan) {
        if (plan.getPatientId() == null) {
            return null;
        }
        return patientRepository.findById(plan.getPatientId())
                .map(Patient::getDateBirth)
                .orElse(null);
    }
}
