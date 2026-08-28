package br.com.nutriplan.auth.web;

import br.com.nutriplan.auth.dto.SignupRequest;
import br.com.nutriplan.auth.dto.LoginRequest;
import br.com.nutriplan.auth.dto.TokenResponse;
import br.com.nutriplan.auth.service.AuthenticationService;
import br.com.nutriplan.auth.service.PasswordRecoveryService;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.auth.service.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacao")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final PasswordRecoveryService recoveryService;
    private final CurrentContext contextCurrent;

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve um token JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authenticationService.login(req);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta de nutricionista e já autentica")
    public TokenResponse register(@Valid @RequestBody SignupRequest req) {
        return authenticationService.register(req);
    }

    @GetMapping("/eu")
    @Operation(summary = "Dados do usuário autenticado")
    public ResponseEntity<TokenResponse.UserSummary> eu() {
        AuthenticatedUser u = contextCurrent.requireUser();
        return ResponseEntity.ok(new TokenResponse.UserSummary(
                u.getUserId(), u.getName(), u.getEmail(), u.getRole(), u.getAccountId(), u.getPlan()));
    }

    // ------------------------------------------------------------ recovery

    public record RecoveryRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email String email) {}

    public record ResetRequest(
            @jakarta.validation.constraints.NotBlank String token,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8, message = "A senha precisa de ao menos 8 caracteres")
            String novaPassword) {}

    @PostMapping("/recover-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pede um link de redefinição de senha",
            description = "Responde 204 sempre, exista o e-mail ou não. Um endpoint que "
                    + "respondesse \"e-mail não encontrado\" seria um oráculo: daria para "
                    + "varrer uma lista de endereços e descobrir quem tem conta.")
    public void recoverPassword(@Valid @RequestBody RecoveryRequest req) {
        recoveryService.request(req.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefine a senha com o token recebido",
            description = "O token vale uma vez só e por uma hora. Redefinir derruba as sessões "
                    + "abertas: quem estava com a senha antiga sai imediatamente.")
    public void resetPassword(@Valid @RequestBody ResetRequest req) {
        recoveryService.reset(req.token(), req.novaPassword());
    }
}
