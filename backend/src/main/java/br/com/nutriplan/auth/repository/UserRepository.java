package br.com.nutriplan.auth.repository;

import br.com.nutriplan.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u join fetch u.account where lower(u.email) = lower(:email)")
    Optional<User> findByEmailComAccount(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** The practice's users, active and inactive: the list is there to manage them. */
    @Query("select u from User u where u.account.id = :accountId order by u.name")
    java.util.List<User> findByAccountIdOrderByNameAsc(@org.springframework.data.repository.query.Param("accountId") Long accountId);

    /** Professional answerable for the account, to sign the patient's plan. */
    Optional<User> findFirstByAccountIdAndRoleAndActiveTrue(
            Long accountId, br.com.nutriplan.auth.domain.Role role);
}
