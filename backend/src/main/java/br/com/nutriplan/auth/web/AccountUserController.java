package br.com.nutriplan.auth.web;

import br.com.nutriplan.auth.service.AccountUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The practice's team.
 *
 * Only the owning nutritionist manages users — and the rule lives in
 * SecurityConfig, together with the others, and not scattered in a per-method
 * annotation.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Equipe do consultório")
public class AccountUserController {

    private final AccountUserService userService;

    @GetMapping
    @Operation(summary = "Lista os usuários do consultório")
    public List<AccountUserService.UserResponse> list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma secretária",
            description = "Dá acesso operacional — agenda e cadastro de pacientes — sem "
                    + "prescrição nem financeiro. Existe para o nutricionista não precisar "
                    + "emprestar a própria senha, o que tornaria a auditoria inútil.")
    public AccountUserService.UserResponse create(
            @Valid @RequestBody AccountUserService.UserRequest req) {
        return userService.createAssistant(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativa o usuário",
            description = "A sessão aberta cai junto, e não só o próximo login.")
    public void deactivate(@PathVariable Long id) {
        userService.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reativa o usuário")
    public AccountUserService.UserResponse reactivate(@PathVariable Long id) {
        return userService.reactivate(id);
    }
}
