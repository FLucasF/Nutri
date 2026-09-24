package br.com.nutriplan.schedule.repository;

import br.com.nutriplan.schedule.domain.Appointment;
import br.com.nutriplan.schedule.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndAccountId(Long id, Long accountId);

    /**
     * Conflict candidates: appointments that occupy an hour and begin inside a
     * window around the intended interval.
     *
     * The exact overlap is **not** decided here. Computing the end in JPQL
     * would require a database-specific date function, which would break
     * portability between H2 and PostgreSQL (AD-11). Instead, the query cuts
     * out a small set — the appointments around it — and the decision stays
     * with {@code Appointment.overlaps}, where the rule already lives.
     *
     * The filter by account is what keeps the schedules of different practices
     * independent; canceled ones are excluded because they free the hour.
     */
    @Query("""
           select a from Appointment a
           where a.accountId = :accountId
             and a.status <> br.com.nutriplan.schedule.domain.AppointmentStatus.CANCELED
             and a.start > :windowStart
             and a.start < :windowEnd
           """)
    List<Appointment> candidatesConflict(@Param("accountId") Long accountId,
                                          @Param("windowStart") LocalDateTime windowStart,
                                          @Param("windowEnd") LocalDateTime windowEnd);

    @Query("""
           select a from Appointment a
           where a.accountId = :accountId
             and a.start >= :from and a.start < :to
             and (:status is null or a.status = :status)
           order by a.start asc
           """)
    List<Appointment> inRange(@Param("accountId") Long accountId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("status") AppointmentStatus status);

    List<Appointment> findByAccountIdAndPatientIdOrderByStartDesc(Long accountId, Long patientId);

    long countByAccountIdAndPatientIdAndStatus(Long accountId, Long patientId,
                                                AppointmentStatus status);

    /** A última consulta realizada de cada paciente, para achar quem sumiu. */
    @Query("""
           select a.patientId, max(a.start) from Appointment a
           where a.accountId = :accountId
             and a.status = br.com.nutriplan.schedule.domain.AppointmentStatus.COMPLETED
           group by a.patientId
           """)
    List<Object[]> lastCompletedByPatient(@Param("accountId") Long accountId);
}
