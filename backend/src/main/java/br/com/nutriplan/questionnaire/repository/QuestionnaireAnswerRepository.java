package br.com.nutriplan.questionnaire.repository;

import br.com.nutriplan.questionnaire.domain.QuestionnaireAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionnaireAnswerRepository
        extends JpaRepository<QuestionnaireAnswer, Long> {

    @Query("""
           select r from QuestionnaireAnswer r join fetch r.questionnaire
           where r.patientId = :patientId and r.accountId = :accountId
           order by r.sentAt desc
           """)
    List<QuestionnaireAnswer> forPatient(@Param("patientId") Long patientId,
                                            @Param("accountId") Long accountId);

    @Query("""
           select r from QuestionnaireAnswer r join fetch r.questionnaire
           where r.appointmentId = :appointmentId and r.accountId = :accountId
           order by r.sentAt desc
           """)
    List<QuestionnaireAnswer> forAppointment(@Param("appointmentId") Long appointmentId,
                                               @Param("accountId") Long accountId);

    Optional<QuestionnaireAnswer> findByPublicIdentifier(String publicIdentifier);

    @Query("""
           select r from QuestionnaireAnswer r join fetch r.questionnaire
           where r.id = :id and r.accountId = :accountId
           """)
    Optional<QuestionnaireAnswer> accountFind(@Param("id") Long id,
                                                   @Param("accountId") Long accountId);
}
