package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Conta;
import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Usuario;
import br.com.nutriplan.auth.dto.CadastroRequest;
import br.com.nutriplan.auth.dto.LoginRequest;
import br.com.nutriplan.auth.dto.TokenResponse;
import br.com.nutriplan.auth.repository.ContaRepository;
import br.com.nutriplan.auth.repository.UsuarioRepository;
import br.com.nutriplan.shared.error.RegraDeNegocioException;
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
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final ContaRepository contaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.senha()));

        var usuario = (UsuarioAutenticado) auth.getPrincipal();
        log.info("Login efetuado: usuário={} conta={}", usuario.getUsuarioId(), usuario.getContaId());

        return TokenResponse.de(jwtService.gerar(usuario), jwtService.validadeEmSegundos(), usuario);
    }

    /**
     * Cria conta + usuario dono. O e-mail e a chave global de identidade, entao
     * duplicidade e barrada antes de tocar o banco (a unique constraint continua
     * sendo a garantia real sob concorrencia).
     */
    @Transactional
    public TokenResponse cadastrar(CadastroRequest req) {
        if (usuarioRepository.existsByEmailIgnoreCase(req.email())) {
            throw new RegraDeNegocioException("Já existe um usuário com este e-mail");
        }

        Conta conta = contaRepository.save(new Conta("Consultório de " + req.nome()));

        Usuario usuario = new Usuario(
                req.nome(),
                req.email().toLowerCase(),
                passwordEncoder.encode(req.senha()),
                Perfil.NUTRICIONISTA,
                conta);
        usuario.setCrn(req.crn());
        usuario.setTelefone(req.telefone());
        usuarioRepository.save(usuario);

        log.info("Conta criada: conta={} usuário={}", conta.getId(), usuario.getId());

        var autenticado = new UsuarioAutenticado(usuario);
        return TokenResponse.de(jwtService.gerar(autenticado), jwtService.validadeEmSegundos(), autenticado);
    }
}
