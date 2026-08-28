package br.com.nutriplan.finance.repository;

import br.com.nutriplan.finance.domain.FinanceTransaction;
import br.com.nutriplan.finance.domain.TransactionStatus;
import br.com.nutriplan.finance.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {

    Optional<FinanceTransaction> findByIdAndAccountId(Long id, Long accountId);

    @Query("""
           select l from FinanceTransaction l
           where l.accountId = :accountId
             and (:from is null or l.accrual >= :from)
             and (:to is null or l.accrual <= :to)
             and (:type is null or l.type = :type)
             and (:status is null or l.status = :status)
             and (:patientId is null or l.patientId = :patientId)
           order by l.accrual desc, l.id desc
           """)
    Page<FinanceTransaction> find(@Param("accountId") Long accountId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("type") TransactionType type,
                                      @Param("status") TransactionStatus status,
                                      @Param("patientId") Long patientId,
                                      Pageable pageable);

    /** Transactions of the accrual period, for settling it. */
    @Query("""
           select l from FinanceTransaction l
           where l.accountId = :accountId
             and l.accrual >= :from and l.accrual <= :to
           """)
    List<FinanceTransaction> forAccrual(@Param("accountId") Long accountId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Outstanding items already overdue at the reference date.
     *
     * The date comes from outside and not from {@code current_date} so that the
     * report is reproducible: settling "those overdue on 15/09" must give the
     * same result today and a month from now.
     */
    @Query("""
           select l from FinanceTransaction l
           where l.accountId = :accountId
             and l.status = br.com.nutriplan.finance.domain.TransactionStatus.PENDING
             and l.due is not null
             and l.due < :reference
           order by l.due asc
           """)
    List<FinanceTransaction> overdueAt(@Param("accountId") Long accountId,
                                          @Param("reference") LocalDate reference);

    List<FinanceTransaction> findByAccountIdAndPatientIdOrderByAccrualDesc(
            Long accountId, Long patientId);

    long countByAccountIdAndAppointmentId(Long accountId, Long appointmentId);
}
