package cookie.server.service;

import cookie.server.dto.PrestigeStatusDto;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class PrestigeService {

    private final UserRepository userRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final BakeJobRepository bakeJobRepository;
    private final NetWorthService netWorthService;

    public PrestigeService(UserRepository userRepository,
                           PlayerUpgradeRepository playerUpgradeRepository,
                           BakeJobRepository bakeJobRepository,
                           @Lazy NetWorthService netWorthService) {
        this.userRepository = userRepository;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.netWorthService = netWorthService;
    }

    public static double calcThreshold(int level) {
        return 100_000 * Math.pow(1.5, level);
    }

    public static double calcMultiplier(int level) {
        return 1.0 + 0.1 * level;
    }

    public PrestigeStatusDto getStatus(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        double netWorth = netWorthService.getNetWorthForUser(userId).getNetWorth();
        double threshold = calcThreshold(user.getPrestigeLevel());

        PrestigeStatusDto dto = new PrestigeStatusDto();
        dto.setPrestigeLevel(user.getPrestigeLevel());
        dto.setTotalPrestiges(user.getTotalPrestiges());
        dto.setMultiplier(calcMultiplier(user.getPrestigeLevel()));
        dto.setCurrentNetWorth(netWorth);
        dto.setThreshold(threshold);
        dto.setCanPrestige(netWorth >= threshold);
        return dto;
    }

    @Transactional
    public PrestigeStatusDto prestige(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        double netWorth = netWorthService.getNetWorthForUser(userId).getNetWorth();
        double threshold = calcThreshold(user.getPrestigeLevel());
        if (netWorth < threshold) {
            throw new IllegalStateException(
                "Net Worth " + netWorth + " unter Schwelle " + threshold);
        }

        // Reset: Cookies, Ressourcen, Upgrades, Bake-Jobs
        user.setCookies(0);
        user.setSugar(0); user.setFlour(0); user.setEggs(0);
        user.setButter(0); user.setChocolate(0); user.setMilk(0);
        user.setPrestigeLevel(user.getPrestigeLevel() + 1);
        user.setTotalPrestiges(user.getTotalPrestiges() + 1);
        userRepository.save(user);

        playerUpgradeRepository.deleteAll(playerUpgradeRepository.findByUserId(userId));
        bakeJobRepository.deleteAll(bakeJobRepository.findAllByUserIdAndClaimedFalse(userId));

        return getStatus(userId);
    }
}
