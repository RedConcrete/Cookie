package cookie.server.scheduler;

import cookie.server.entity.UserEntity;
import cookie.server.repository.UserRepository;
import cookie.server.service.WageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WageScheduler {
    private static final Logger log = LoggerFactory.getLogger(WageScheduler.class);
    private static final int MAX_RETRIES = 3;

    private final UserRepository userRepository;
    private final WageService wageService;

    public WageScheduler(UserRepository userRepository, WageService wageService) {
        this.userRepository = userRepository;
        this.wageService = wageService;
    }

    @Scheduled(fixedRate = 60_000)
    public void deductWages() {
        List<UserEntity> users = userRepository.findAll();
        for (UserEntity user : users) {
            deductWithRetry(user.getSteamId());
        }
    }

    // Eigene Transaktion pro Spieler (siehe WageService) + Retry bei Optimistic-Lock-Konflikt
    // mit dem PassiveIncomeScheduler, der alle 5s parallel auf denselben User schreiben kann.
    private void deductWithRetry(String userId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                wageService.deductWageForUser(userId);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES) {
                    log.warn("Wage deduction: giving up on {} after {} attempts (lock conflict)", userId, MAX_RETRIES);
                }
            } catch (Exception e) {
                log.error("Wage deduction failed for {}", userId, e);
                return;
            }
        }
    }
}
