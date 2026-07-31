package cookie.server.scheduler;

import cookie.server.entity.UserEntity;
import cookie.server.repository.UserRepository;
import cookie.server.service.BuildingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class WageScheduler {
    private static final Logger log = LoggerFactory.getLogger(WageScheduler.class);

    private final UserRepository userRepository;
    private final BuildingService buildingService;

    public WageScheduler(UserRepository userRepository, BuildingService buildingService) {
        this.userRepository = userRepository;
        this.buildingService = buildingService;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void deductWages() {
        List<UserEntity> users = userRepository.findAll();
        for (UserEntity user : users) {
            try {
                double wage = buildingService.getTotalWage(user.getSteamId());
                if (wage <= 0) {
                    if (user.isWorkersIdle()) { user.setWorkersIdle(false); userRepository.save(user); }
                    continue;
                }
                if (user.getCookies() >= wage) {
                    user.setCookies(user.getCookies() - wage);
                    user.setWorkersIdle(false);
                } else {
                    user.setWorkersIdle(true);
                }
                userRepository.save(user);
            } catch (Exception e) {
                log.error("Wage deduction failed for {}: {}", user.getSteamId(), e.getMessage());
            }
        }
    }
}
