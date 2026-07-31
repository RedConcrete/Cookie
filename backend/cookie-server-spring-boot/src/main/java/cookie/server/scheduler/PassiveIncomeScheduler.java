package cookie.server.scheduler;

import cookie.server.entity.UserEntity;
import cookie.server.repository.UserRepository;
import cookie.server.service.BuildingService;
import cookie.server.service.MarketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PassiveIncomeScheduler {
    private static final Logger log = LoggerFactory.getLogger(PassiveIncomeScheduler.class);
    private static final double TICK_SECONDS = 5.0;

    private final UserRepository userRepository;
    private final BuildingService buildingService;
    private final MarketService marketService;

    public PassiveIncomeScheduler(UserRepository userRepository,
                                  BuildingService buildingService,
                                  MarketService marketService) {
        this.userRepository = userRepository;
        this.buildingService = buildingService;
        this.marketService = marketService;
    }

    @Scheduled(fixedRate = 5_000)
    @Transactional
    public void tick() {
        if (userRepository.count() == 0) return;
        List<UserEntity> users = userRepository.findAll();
        for (UserEntity user : users) {
            if (user.isWorkersIdle()) continue;
            try {
                List<BuildingService.PassiveTick> ticks =
                        buildingService.computePassiveTicks(user.getSteamId(), TICK_SECONDS);
                if (ticks.isEmpty()) continue;

                double cap = buildingService.getTotalCap(user.getSteamId());
                double total = user.getSugar() + user.getFlour() + user.getEggs()
                             + user.getButter() + user.getChocolate() + user.getMilk();

                for (BuildingService.PassiveTick t : ticks) {
                    double available = Math.max(0, cap - total);
                    double toAdd     = Math.min(t.amount(), available);
                    double overflow  = t.amount() - toAdd;

                    if (overflow > 0) {
                        double price  = marketService.getCurrentPrice(t.resource());
                        double payout = overflow * price * (1.0 - marketService.getSellFeeRate());
                        user.setCookies(user.getCookies() + payout);
                    }

                    switch (t.resource()) {
                        case SUGAR     -> { user.setSugar(user.getSugar() + toAdd);         total += toAdd; }
                        case FLOUR     -> { user.setFlour(user.getFlour() + toAdd);         total += toAdd; }
                        case EGGS      -> { user.setEggs(user.getEggs() + toAdd);           total += toAdd; }
                        case BUTTER    -> { user.setButter(user.getButter() + toAdd);       total += toAdd; }
                        case CHOCOLATE -> { user.setChocolate(user.getChocolate() + toAdd); total += toAdd; }
                        case MILK      -> { user.setMilk(user.getMilk() + toAdd);           total += toAdd; }
                    }
                }
                userRepository.save(user);
            } catch (Exception e) {
                log.error("Passive tick failed for {}: {}", user.getSteamId(), e.getMessage());
            }
        }
    }
}
