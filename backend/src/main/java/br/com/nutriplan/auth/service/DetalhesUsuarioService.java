package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DetalhesUsuarioService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UsuarioAutenticado loadUserByUsername(String email) {
        return usuarioRepository.buscarPorEmailComConta(email)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticado carregarPorId(Long id) {
        return usuarioRepository.findById(id)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + id));
    }
}
