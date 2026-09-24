package br.com.nutriplan.patient.repository;

import br.com.nutriplan.patient.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every method takes the accountId explicitly. There is no query without that
 * filter by design: it is what prevents one practice from reading another's
 * data.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByIdAndAccountId(Long id, Long accountId);

    /*
     * O termo de busca vai com tipo declarado.
     *
     * Sem o cast, um termo nulo dentro do concat faz o PostgreSQL assumir
     * bytea e recusar a consulta inteira. So aparece no banco de verdade: o H2
     * em modo de compatibilidade assume texto e responde normalmente.
     */
    @Query("""
           select p from Patient p
           where p.accountId = :accountId
             and (:active is null or p.active = :active)
             and (cast(:term as string) is null
                  or lower(p.name)     like lower(concat('%', cast(:term as string), '%'))
                  or lower(p.email)    like lower(concat('%', cast(:term as string), '%'))
                  or p.phone        like concat('%', cast(:term as string), '%'))
           """)
    Page<Patient> find(@Param("accountId") Long accountId,
                          @Param("term") String term,
                          @Param("active") Boolean active,
                          Pageable pageable);

    long countByAccountIdAndActiveTrue(Long accountId);

    boolean existsByAccountIdAndCpf(Long accountId, String cpf);

    Optional<Patient> findByUserId(Long userId);

    /** Todos os pacientes da conta, para as estatísticas somarem em memória. */
    List<Patient> findByAccountId(Long accountId);

    /** Os que vieram por indicação, para o relatório de parceiros. */
    List<Patient> findByAccountIdAndPartnerIdIsNotNull(Long accountId);
}
