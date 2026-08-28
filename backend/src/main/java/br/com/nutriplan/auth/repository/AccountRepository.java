package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Schedule subscription: the account comes from the feed's address. */
    java.util.Optional<br.com.nutriplan.auth.domain.Account> findByTokenSchedule(String tokenSchedule);
}
