package br.com.nutriplan.schedule.service;

import br.com.nutriplan.auth.domain.Account;
import br.com.nutriplan.auth.repository.AccountRepository;
import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Schedule subscription in an external calendar.
 *
 * It serves an iCalendar feed at an address with a UUID, which Google Calendar,
 * Apple Calendar and Outlook subscribe to natively. The calendar fetches the
 * address from time to time and reflects what changed.
 *
 * <p><b>What this is not.</b> It is not an integration with the Google API.
 * That would require an application credential, a reviewed consent screen and a
 * second token system to maintain, and would give back the same thing the
 * professional wants: seeing the appointments in the calendar they already use.
 * What is left out is the way back — creating an appointment here from an event
 * created in Google.
 *
 * <p><b>The caveat.</b> Whoever receives the address sees the schedule, with
 * patient names. It is the same authorization by possession of a link as the
 * public plan, and so the address only exists after the nutritionist asks for
 * it, and can be regenerated — which invalidates the previous one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleSubscriptionService {

    /**
     * Window of the feed.
     *
     * One month back because the user's calendar usually shows the whole
     * current month; six forward because a practice's schedule rarely goes
     * beyond that, and serving the complete history would make the file grow
     * without limit with every year of use.
     */
    private static final int MONTHS_TO_TRAS = 1;
    private static final int MONTHS_TO_FRONT = 6;

    private final AccountRepository accountRepository;
    private final AppointmentService appointmentService;
    private final IcsCalendar calendar;
    private final CurrentContext contextCurrent;

    public record Subscription(String token) {}

    @Transactional(readOnly = true)
    public Subscription current() {
        return new Subscription(requireAccount().getTokenSchedule());
    }

    /** Generates or regenerates. Regenerating invalidates the address already handed out. */
    @Transactional
    public Subscription generate() {
        Account account = requireAccount();
        String token = account.generateScheduleToken();
        log.info("Assinatura da agenda gerada: conta={}", account.getId());
        return new Subscription(token);
    }

    @Transactional
    public void revoke() {
        requireAccount().setTokenSchedule(null);
    }

    /**
     * The calendar, served without a credential — authorized by possession of
     * the address.
     */
    @Transactional(readOnly = true)
    public String calendarDe(String token) {
        Account account = accountRepository.findByTokenSchedule(token)
                .orElseThrow(() -> new NotFoundException(
                        "Agenda não encontrada. Confira o endereço da assinatura."));

        LocalDate today = LocalDate.now();
        var appointments = appointmentService.naAccountRange(
                account.getId(),
                today.minusMonths(MONTHS_TO_TRAS),
                today.plusMonths(MONTHS_TO_FRONT));

        return calendar.generate(account.getName(), appointments);
    }

    private Account requireAccount() {
        return accountRepository.findById(contextCurrent.accountId())
                .orElseThrow(() -> new NotFoundException(
                        "Conta", contextCurrent.accountId()));
    }
}
