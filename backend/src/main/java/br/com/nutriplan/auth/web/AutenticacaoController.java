package br.com.nutriplan.auth.web;

import br.com.nutriplan.auth.dto.CadastroRequest;
import br.com.nutriplan.auth.dto.LoginRequest;
import br.com.nutriplan.auth.dto.TokenResponse;
import br.com.nutriplan.auth.service.AutenticacaoService;
import br.com.nutriplan.auth.service.RecuperacaoDeSenhaService;
import br.com.nutriplan.auth.service.ContextoAtual;
import br.com.nutriplan.auth.service.UsuarioAutenticado;
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
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final RecuperacaoDeSenhaService recuperacaoService;
    private final ContextoAtual contextoAtual;

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve um token JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return autenticacaoService.login(req);
    }

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma conta de nutricionista e já autentica")
    public TokenResponse cadastrar(@Valid @RequestBody CadastroRequest req) {
        return autenticacaoService.cadastrar(req);
    }

    @GetMapping("/eu")
    @Operation(summary = "Dados do usuário autenticado")
    public ResponseEntity<TokenResponse.UsuarioResumo> eu() {
        UsuarioAutenticado u = contextoAtual.exigirUsuario();
        return ResponseEntity.ok(new TokenResponse.UsuarioResumo(
                u.getUsuarioId(), u.getNome(), u.getEmail(), u.getPerfil(), u.getContaId(), u.getPlano()));
    }

    // --------------------------------------------------------- recuperação

    public record RecuperacaoRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email String email) {}

    public record RedefinicaoRequest(
            @jakarta.validation.constraints.NotBlank String token,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8, message = "A senha precisa de ao menos 8 caracteres")
            String novaSenha) {}

    @PostMapping("/recuperar-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pede um link de redefinição de senha",
            description = "Responde 204 sempre, exista o e-mail ou não. Um endpoint que "
                    + "respondesse \"e-mail não encontrado\" seria um oráculo: daria para "
                    + "varrer uma lista de endereços e descobrir quem tem conta.")
    public void recuperarSenha(@Valid @RequestBody RecuperacaoRequest req) {
        recuperacaoService.solicitar(req.email());
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefine a senha com o token recebido",
            description = "O token vale uma vez só e por uma hora. Redefinir derruba as sessões "
                    + "abertas: quem estava com a senha antiga sai imediatamente.")
    public void redefinirSenha(@Valid @RequestBody RedefinicaoRequest req) {
        recuperacaoService.redefinir(req.token(), req.novaSenha());
    }
}
