package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserByUsername(String email) {
        return userRepository.findByEmailComAccount(email)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + email));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadById(Long id) {
        return userRepository.findById(id)
                .map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + id));
    }
}
