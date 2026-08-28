package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Usuario;
import br.com.nutriplan.auth.repository.UsuarioRepository;
import br.com.nutriplan.shared.error.RecursoNaoEncontradoException;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Usuários do consultório.
 *
 * Existe para o nutricionista dar acesso à secretária sem entregar a própria
 * senha — que é o que acontece hoje em consultório que não tem esse recurso, e
 * é pior que qualquer falha de permissão: um login compartilhado torna o registro
 * de auditoria inútil, porque toda ação fica no nome do dono.
 *
 * O que a secretária pode fazer está nas regras de autorização, e não aqui:
 * agenda e cadastro sim, prescrição e financeiro não. A separação é do domínio,
 * não de conveniência — prescrever é ato privativo do nutricionista.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioDaContaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificador;
    private final ContextoAtual contextoAtual;

    public record UsuarioRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 150) String nome,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 180) String email,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8,
                    message = "A senha precisa de ao menos 8 caracteres")
            String senhaInicial,
            @jakarta.validation.constraints.Size(max = 20) String telefone
    ) {}

    public record UsuarioResponse(
            Long id, String nome, String email, Perfil perfil,
            String perfilDescricao, boolean ativo, Instant criadoEm) {

        static UsuarioResponse de(Usuario u) {
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                    descricaoDe(u.getPerfil()), u.isAtivo(), u.getCriadoEm());
        }

        private static String descricaoDe(Perfil perfil) {
            return switch (perfil) {
                case NUTRICIONISTA -> "Nutricionista";
                case SECRETARIA -> "Secretária";
                case PACIENTE -> "Paciente";
                case ADMIN -> "Administrador";
            };
        }
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByContaIdOrderByNomeAsc(contextoAtual.contaId()).stream()
                .map(UsuarioResponse::de)
                .toList();
    }

    /**
     * Cria uma secretária no consultório.
     *
     * A senha inicial é escolhida por quem cadastra, e a secretária pode
     * trocá-la pela recuperação. Enviar um link de definição seria melhor, e é
     * o que um sistema com mensageria faria — mas mensageria está fora do
     * escopo (§1), e uma senha inicial combinada pessoalmente é o que resta
     * sem fingir um envio que não acontece.
     */
    @Transactional
    public UsuarioResponse criarSecretaria(UsuarioRequest req) {
        var dono = contextoAtual.exigirUsuario();

        if (usuarioRepository.existsByEmailIgnoreCase(req.email().trim())) {
            throw new RegraDeNegocioException("Já existe uma conta com este e-mail");
        }

        var usuario = new Usuario();
        usuario.setNome(req.nome().trim());
        usuario.setEmail(req.email().trim().toLowerCase());
        usuario.setSenhaHash(codificador.encode(req.senhaInicial()));
        usuario.setPerfil(Perfil.SECRETARIA);
        usuario.setTelefone(req.telefone());
        usuario.setConta(usuarioRepository.findById(dono.getUsuarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", dono.getUsuarioId()))
                .getConta());

        usuarioRepository.save(usuario);
        log.info("Secretaria cadastrada: id={} conta={}", usuario.getId(), dono.getContaId());
        return UsuarioResponse.de(usuario);
    }

    @Transactional
    public void inativar(Long id) {
        Usuario usuario = exigirDaConta(id);
        if (usuario.getPerfil() == Perfil.NUTRICIONISTA) {
            throw new RegraDeNegocioException(
                    "O nutricionista dono da conta não pode ser desativado");
        }
        usuario.setAtivo(false);
        // Incrementa a versao da senha junto: desativar precisa derrubar a
        // sessao aberta, e nao so impedir o proximo login.
        usuario.trocarSenha(usuario.getSenhaHash(), Instant.now());
        log.info("Usuário inativado: id={}", id);
    }

    @Transactional
    public UsuarioResponse reativar(Long id) {
        Usuario usuario = exigirDaConta(id);
        usuario.setAtivo(true);
        return UsuarioResponse.de(usuario);
    }

    private Usuario exigirDaConta(Long id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getConta() != null
                        && u.getConta().getId().equals(contextoAtual.contaId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }
}
