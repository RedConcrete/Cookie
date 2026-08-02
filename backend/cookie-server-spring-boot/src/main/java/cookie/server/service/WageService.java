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

        double wage = buildingService.getTotalWage(userId);
        if (wage <= 0) {
            if (user.isWorkersIdle()) {
                user.setWorkersIdle(false);
                userRepository.save(user);
            }
            return;
        }

        if (user.getCookies() >= wage) {
            user.setCookies(user.getCookies() - wage);
            user.setWorkersIdle(false);
        } else {
            user.setWorkersIdle(true);
        }
        userRepository.save(user);
    }
}
