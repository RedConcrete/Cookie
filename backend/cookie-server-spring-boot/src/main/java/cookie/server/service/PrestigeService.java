package cookie.server.service;

import cookie.server.config.GameBalanceConfig;
import cookie.server.dto.PrestigeStatusDto;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.PlayerSkillNodeRepository;
import cookie.server.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class PrestigeService {

    private final UserRepository userRepository;
    private final PlayerSkillNodeRepository playerSkillNodeRepository;
    private final BakeJobRepository bakeJobRepository;
    private final PlayerBuildingRepository playerBuildingRepository;
    private final NetWorthService netWorthService;
    private final GameBalanceConfig balance;

    public PrestigeService(UserRepository userRepository,
                           PlayerSkillNodeRepository playerSkillNodeRepository,
                           BakeJobRepository bakeJobRepository,
                           PlayerBuildingRepository playerBuildingRepository,
                           @Lazy NetWorthService netWorthService,
                           GameBalanceConfig balance) {
        this.userRepository = userRepository;
        this.playerSkillNodeRepository = playerSkillNodeRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.playerBuildingRepository = playerBuildingRepository;
        this.netWorthService = netWorthService;
        this.balance = balance;
    }

    public double calcThreshold(int level) {
        return balance.getPrestigeBaseThreshold() * Math.pow(balance.getPrestigeThresholdGrowth(), level);
    }

    public double calcMultiplier(int level) {
        return 1.0 + balance.getPrestigeMultiplierPerLevel() * level;
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

        // Reset: Cookies, Ressourcen, Skill Tree, Bake-Jobs
        user.setCookies(0);
        user.setSugar(0); user.setFlour(0); user.setEggs(0);
        user.setButter(0); user.setChocolate(0); user.setMilk(0);
        user.setPrestigeLevel(user.getPrestigeLevel() + 1);
        user.setTotalPrestiges(user.getTotalPrestiges() + 1);
        // Skill-Punkt-Kostenkurve geht bei Prestige zurueck auf billig -- anders als
        // totalPrestiges, das dauerhaft bleibt (siehe cookie-game-design.md §11).
        user.setSkillPoints(0);
        user.setTotalSkillPointsBought(0);
        user.setTotalSkillPointCookiesSpent(0);
        userRepository.save(user);

        playerSkillNodeRepository.deleteByUserId(userId);
        bakeJobRepository.deleteAll(bakeJobRepository.findAllByUserIdAndClaimedFalse(userId));
        playerBuildingRepository.deleteByUserId(userId);

        return getStatus(userId);
    }
}
