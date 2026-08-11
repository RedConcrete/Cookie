package cookie.server.service;

import cookie.server.config.PlayerConfig;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.NetWorthHistoryRepository;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.PlayerSkillNodeRepository;
import cookie.server.repository.UserRepository;
import cookie.server.repository.WageLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Self-service hard reset (bankruptcy trigger or manual settings action). Wipes a player's
 * current run -- cookies/resources/buildings/skill tree/prestige/debt history -- back to a
 * fresh-account state, but leaves lifetime stat counters and Steam identity untouched.
 */
@Service
public class PlayerResetService {

    private final UserRepository userRepository;
    private final PlayerConfig playerConfig;
    private final PlayerSkillNodeRepository skillNodeRepository;
    private final PlayerBuildingRepository buildingRepository;
    private final BakeJobRepository bakeJobRepository;
    private final NetWorthHistoryRepository netWorthHistoryRepository;
    private final WageLedgerRepository wageLedgerRepository;

    public PlayerResetService(UserRepository userRepository, PlayerConfig playerConfig,
                              PlayerSkillNodeRepository skillNodeRepository,
                              PlayerBuildingRepository buildingRepository,
                              BakeJobRepository bakeJobRepository,
                              NetWorthHistoryRepository netWorthHistoryRepository,
                              WageLedgerRepository wageLedgerRepository) {
        this.userRepository = userRepository;
        this.playerConfig = playerConfig;
        this.skillNodeRepository = skillNodeRepository;
        this.buildingRepository = buildingRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.netWorthHistoryRepository = netWorthHistoryRepository;
        this.wageLedgerRepository = wageLedgerRepository;
    }

    @Transactional
    public void hardReset(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        user.setCookies(playerConfig.getInitialCookies());
        user.setSugar(playerConfig.getInitialSugar());
        user.setFlour(playerConfig.getInitialFlour());
        user.setEggs(playerConfig.getInitialEggs());
        user.setButter(playerConfig.getInitialButter());
        user.setChocolate(playerConfig.getInitialChocolate());
        user.setMilk(playerConfig.getInitialMilk());
        user.setWorkersIdle(false);
        user.setOwnedCitizens(0);
        user.setSkillPoints(playerConfig.getInitialSkillPoints());
        user.setTotalSkillPointsBought(0);
        user.setTotalSkillPointCookiesSpent(0);
        user.setPrestigeLevel(0);
        user.setTotalPrestiges(0);
        user.setLastWageAmount(0);
        user.setLastWageAt(null);
        userRepository.save(user);

        skillNodeRepository.deleteByUserId(userId);
        buildingRepository.deleteByUserId(userId);
        bakeJobRepository.deleteByUserId(userId);
        netWorthHistoryRepository.deleteByUserId(userId);
        wageLedgerRepository.deleteByUserId(userId);
    }
}
