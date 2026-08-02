package cookie.server.scheduler;

import cookie.server.config.GameBalanceConfig;
import cookie.server.entity.UserEntity;
import cookie.server.repository.UserRepository;
import cookie.server.service.PassiveIncomeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PassiveIncomeScheduler {
    private static final Logger log = LoggerFactory.getLogger(PassiveIncomeScheduler.class);
    private static final int MAX_RETRIES = 3;

    private final UserRepository userRepository;
    private final PassiveIncomeService passiveIncomeService;
    private final GameBalanceConfig balance;

    public PassiveIncomeScheduler(UserRepository userRepository,
                                  PassiveIncomeService passiveIncomeService,
                                  GameBalanceConfig balance) {
        this.userRepository = userRepository;
        this.passiveIncomeService = passiveIncomeService;
        this.balance = balance;
    }

    // Feste 5s-Kadenz -- @Scheduled braucht einen zur Startzeit bekannten Wert, kann also nicht
    // aus der live editierbaren GameBalanceConfig gelesen werden (die Admin-Panel-Aenderung
    // wirkt sich aber sofort auf die PRODUZIERTE MENGE pro Tick aus, siehe balance.getPassiveTickSeconds()
    // unten -- nur das Intervall selbst braucht einen Neustart, wenn es je konfigurierbar werden soll).
    @Scheduled(fixedRate = 5_000)
    public void tick() {
        if (userRepository.count() == 0) return;
        List<UserEntity> users = userRepository.findAll();
        double tickSeconds = balance.getPassiveTickSeconds();
        for (UserEntity user : users) {
            creditWithRetry(user.getSteamId(), tickSeconds);
        }
    }

    // Eigene Transaktion pro Spieler (siehe PassiveIncomeService) + Retry bei Optimistic-Lock-
    // Konflikt mit dem WageScheduler, der parallel auf denselben User schreiben kann.
    private void creditWithRetry(String userId, double tickSeconds) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                passiveIncomeService.creditUser(userId, tickSeconds);
                return;
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_RETRIES) {
                    log.warn("Passive tick: giving up on {} after {} attempts (lock conflict)", userId, MAX_RETRIES);
                }
            } catch (Exception e) {
                log.error("Passive tick failed for {}", userId, e);
                return;
            }
        }
    }
}
