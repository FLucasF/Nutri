package br.com.nutriplan.auth.web;

import br.com.nutriplan.auth.service.UsuarioDaContaService;
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
 * Equipe do consultorio.
 *
 * So o nutricionista dono gere usuarios — e a regra esta em SegurancaConfig,
 * junto das demais, e nao espalhada em anotacao por metodo.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Equipe do consultório")
public class UsuarioDaContaController {

    private final UsuarioDaContaService usuarioService;

    @GetMapping
    @Operation(summary = "Lista os usuários do consultório")
    public List<UsuarioDaContaService.UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma secretária",
            description = "Dá acesso operacional — agenda e cadastro de pacientes — sem "
                    + "prescrição nem financeiro. Existe para o nutricionista não precisar "
                    + "emprestar a própria senha, o que tornaria a auditoria inútil.")
    public UsuarioDaContaService.UsuarioResponse criar(
            @Valid @RequestBody UsuarioDaContaService.UsuarioRequest req) {
        return usuarioService.criarSecretaria(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativa o usuário",
            description = "A sessão aberta cai junto, e não só o próximo login.")
    public void inativar(@PathVariable Long id) {
        usuarioService.inativar(id);
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativa o usuário")
    public UsuarioDaContaService.UsuarioResponse reativar(@PathVariable Long id) {
        return usuarioService.reativar(id);
    }
}
