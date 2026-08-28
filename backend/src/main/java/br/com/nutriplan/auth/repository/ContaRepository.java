package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    /** Assinatura da agenda: a conta vem do endereco do feed. */
    java.util.Optional<br.com.nutriplan.auth.domain.Conta> findByTokenAgenda(String tokenAgenda);
}
