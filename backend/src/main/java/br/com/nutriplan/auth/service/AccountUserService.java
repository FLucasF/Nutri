package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The practice's users.
 *
 * It exists so that the nutritionist can give the receptionist access without
 * handing over their own password — which is what happens today in a practice
 * without this feature, and is worse than any permission flaw: a shared login
 * makes the audit record useless, because every action ends up in the owner's
 * name.
 *
 * What the receptionist can do lives in the authorization rules, and not here:
 * schedule and registration yes, prescription and finance no. The separation
 * comes from the domain, not from convenience — prescribing is an act reserved
 * to the nutritionist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final CurrentContext contextCurrent;

    public record UserRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 180) String email,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8,
                    message = "A senha precisa de ao menos 8 caracteres")
            String initialPassword,
            @jakarta.validation.constraints.Size(max = 20) String phone
    ) {}

    public record UserResponse(
            Long id, String name, String email, Role role,
            String roleDescription, boolean active, Instant createdAt) {

        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                    descriptionDe(u.getRole()), u.isActive(), u.getCreatedAt());
        }

        private static String descriptionDe(Role role) {
            return switch (role) {
                case NUTRITIONIST -> "Nutricionista";
                case ASSISTANT -> "Secretária";
                case PATIENT -> "Paciente";
                case ADMIN -> "Administrador";
            };
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findByAccountIdOrderByNameAsc(contextCurrent.accountId()).stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Creates a receptionist in the practice.
     *
     * The initial password is chosen by whoever registers them, and the
     * receptionist can change it through recovery. Sending a set-password link
     * would be better, and is what a system with messaging would do — but
     * messaging is out of scope (§1), and an initial password agreed in person
     * is what is left without faking a delivery that does not happen.
     */
    @Transactional
    public UserResponse createAssistant(UserRequest req) {
        var owner = contextCurrent.requireUser();

        if (userRepository.existsByEmailIgnoreCase(req.email().trim())) {
            throw new BusinessRuleException("Já existe uma conta com este e-mail");
        }

        var user = new User();
        user.setName(req.name().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setPasswordHash(encoder.encode(req.initialPassword()));
        user.setRole(Role.ASSISTANT);
        user.setPhone(req.phone());
        user.setAccount(userRepository.findById(owner.getUserId())
                .orElseThrow(() -> new NotFoundException("Usuário", owner.getUserId()))
                .getAccount());

        userRepository.save(user);
        log.info("Secretaria cadastrada: id={} conta={}", user.getId(), owner.getAccountId());
        return UserResponse.from(user);
    }

    @Transactional
    public void deactivate(Long id) {
        User user = accountRequire(id);
        if (user.getRole() == Role.NUTRITIONIST) {
            throw new BusinessRuleException(
                    "O nutricionista dono da conta não pode ser desativado");
        }
        user.setActive(false);
        // It increments the password version along with it: deactivating has to
        // drop the open session, and not merely prevent the next login.
        user.changePassword(user.getPasswordHash(), Instant.now());
        log.info("Usuário inativado: id={}", id);
    }

    @Transactional
    public UserResponse reactivate(Long id) {
        User user = accountRequire(id);
        user.setActive(true);
        return UserResponse.from(user);
    }

    private User accountRequire(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getAccount() != null
                        && u.getAccount().getId().equals(contextCurrent.accountId()))
                .orElseThrow(() -> new NotFoundException("Usuário", id));
    }
}
