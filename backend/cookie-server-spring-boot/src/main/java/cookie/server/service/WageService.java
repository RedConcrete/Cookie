package cookie.server.service;

import cookie.server.entity.UserEntity;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Eigene Transaktionsgrenze pro Spieler fuer den Lohn-Abzug (siehe WageScheduler) --
 * gleicher Grund wie bei PassiveIncomeService: @Transactional greift bei Self-Invocation
 * innerhalb derselben Klasse nicht, und ein Optimistic-Lock-Konflikt bei einem Spieler
 * soll nicht den Lohnlauf fuer alle anderen zurueckrollen.
 */
@Service
public class WageService {

    private final UserRepository userRepository;
    private final BuildingService buildingService;

    public WageService(UserRepository userRepository, BuildingService buildingService) {
        this.userRepository = userRepository;
        this.buildingService = buildingService;
    }

    @Transactional
    public void deductWageForUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        boolean wasIdle = user.isWorkersIdle();
        double wage = buildingService.getTotalWage(userId);
        boolean nowIdle;
        boolean cookiesChanged = false;
        if (wage <= 0) {
            nowIdle = false;
        } else if (user.getCookies() >= wage) {
            user.setCookies(user.getCookies() - wage);
            cookiesChanged = true;
            nowIdle = false;
        } else {
            nowIdle = true;
        }

        if (nowIdle == wasIdle && !cookiesChanged) return;

        // Passive Gebäude-Produktion settlen, bevor der Idle-Status wechselt -- sonst würde
        // die Zeitspanne VOR dem Wechsel fälschlich mit dem NEUEN Status bewertet (siehe
        // BuildingService#settleAllForIdleTransition). Kein eigener Scheduler dafür nötig,
        // läuft im ohnehin vorhandenen 60s-Lohnlauf mit.
        if (nowIdle != wasIdle) {
            buildingService.settleAllForIdleTransition(userId, wasIdle);
        }

        user.setWorkersIdle(nowIdle);
        userRepository.save(user);
    }
}
