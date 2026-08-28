package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Account;
import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.User;
import br.com.nutriplan.auth.dto.SignupRequest;
import br.com.nutriplan.auth.dto.LoginRequest;
import br.com.nutriplan.auth.dto.TokenResponse;
import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.repository.UserRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        var user = (AuthenticatedUser) auth.getPrincipal();
        log.info("Login efetuado: usuário={} conta={}", user.getUserId(), user.getAccountId());

        return TokenResponse.from(jwtService.generate(user), jwtService.expiryAtSeconds(), user);
    }

    /**
     * Creates account + owner user. The email is the global identity key, so
     * duplication is barred before touching the database (the unique constraint
     * remains the real guarantee under concurrency).
     */
    @Transactional
    public TokenResponse register(SignupRequest req) {
        if (userRepository.existsByEmailIgnoreCase(req.email())) {
            throw new BusinessRuleException("Já existe um usuário com este e-mail");
        }

        Account account = accountRepository.save(new Account("Consultório de " + req.name()));

        User user = new User(
                req.name(),
                req.email().toLowerCase(),
                passwordEncoder.encode(req.password()),
                Role.NUTRITIONIST,
                account);
        user.setCrn(req.crn());
        user.setPhone(req.phone());
        userRepository.save(user);

        log.info("Conta criada: conta={} usuário={}", account.getId(), user.getId());

        var authenticated = new AuthenticatedUser(user);
        return TokenResponse.from(jwtService.generate(authenticated), jwtService.expiryAtSeconds(), authenticated);
    }
}
