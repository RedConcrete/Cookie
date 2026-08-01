package cookie.server.service;

import cookie.server.entity.PlayerUpgradeEntity;
import cookie.server.entity.UpgradeEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.UpgradeType;
import cookie.server.dto.UpgradeWithStatusDto;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.UpgradeRepository;
import cookie.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class UpgradeService {

    private final UpgradeRepository upgradeRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final UserRepository userRepository;

    public UpgradeService(UpgradeRepository upgradeRepository,
                          PlayerUpgradeRepository playerUpgradeRepository,
                          UserRepository userRepository) {
        this.upgradeRepository = upgradeRepository;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.userRepository = userRepository;
    }

    // Alte Automation-Upgrade-IDs, ersetzt durch das Einwohner/Gebäude-Kapazitäts-System
    // (siehe BuildingService: Produktionsgebäude sind jetzt selbst upgradeable für mehr
    // Arbeiter-Slots). Wird bei jedem Start entfernt, falls aus einer aelteren DB noch vorhanden.
    private static final List<String> REMOVED_UPGRADE_IDS = List.of(
        "auto_sugar", "auto_flour", "auto_eggs", "auto_butter", "auto_chocolate", "auto_milk"
    );

    @PostConstruct
    public void seedUpgrades() {
        removeObsoleteUpgrades();
        if (upgradeRepository.count() > 0) return;

        upgradeRepository.saveAll(List.of(
            upgrade("boost_harvest", "Schärferes Werkzeug",
                "+0.5 Ressourcen pro Ernte-Tick",
                UpgradeType.BOOST_HARVEST, null, 50, 0.5, 0),

            upgrade("boost_harvest_speed", "Turbopflücker",
                "-100ms Ernte-Intervall pro Stufe (Basis 1000ms, min 200ms)",
                UpgradeType.BOOST_HARVEST, null, 80, 100.0, 8),

            upgrade("boost_bake", "Große Schüssel",
                "+10% Cookie-Ausbeute pro Backen-Batch",
                UpgradeType.BOOST_BAKE, null, 100, 0.10, 0),

            upgrade("extra_oven", "Zweiter Ofen",
                "+1 gleichzeitiger Bake-Job-Slot",
                UpgradeType.CAPACITY, null, 500, 1.0, 3)
        ));
    }

    @Transactional
    protected void removeObsoleteUpgrades() {
        for (String id : REMOVED_UPGRADE_IDS) {
            playerUpgradeRepository.deleteAll(playerUpgradeRepository.findByUpgradeId(id));
            if (upgradeRepository.existsById(id)) upgradeRepository.deleteById(id);
        }
    }

    private UpgradeEntity upgrade(String id, String name, String desc,
                                   UpgradeType type, String target,
                                   double baseCost, double effectPerLevel, int maxLevel) {
        UpgradeEntity e = new UpgradeEntity();
        e.setId(id);
        e.setName(name);
        e.setDescription(desc);
        e.setType(type);
        e.setTargetResource(target);
        e.setBaseCost(baseCost);
        e.setEffectPerLevel(effectPerLevel);
        e.setMaxLevel(maxLevel);
        return e;
    }

    public List<UpgradeWithStatusDto> getUpgradesForPlayer(String userId) {
        List<UpgradeEntity> all = upgradeRepository.findAll();
        Map<String, Integer> levels = playerUpgradeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(PlayerUpgradeEntity::getUpgradeId, PlayerUpgradeEntity::getLevel));

        Map<String, Double> spent = playerUpgradeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(PlayerUpgradeEntity::getUpgradeId, PlayerUpgradeEntity::getTotalSpent));

        return all.stream().map(u -> {
            int level = levels.getOrDefault(u.getId(), 0);
            UpgradeWithStatusDto dto = new UpgradeWithStatusDto();
            dto.setId(u.getId());
            dto.setName(u.getName());
            dto.setDescription(u.getDescription());
            dto.setType(u.getType().name());
            dto.setTargetResource(u.getTargetResource());
            dto.setCurrentLevel(level);
            dto.setMaxLevel(u.getMaxLevel());
            dto.setEffectPerLevel(u.getEffectPerLevel());
            dto.setNextLevelCost(nextCost(u.getBaseCost(), level));
            dto.setTotalSpent(spent.getOrDefault(u.getId(), 0.0));
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public List<UpgradeWithStatusDto> buyUpgrade(String userId, String upgradeId) {
        UpgradeEntity upgrade = upgradeRepository.findById(upgradeId)
                .orElseThrow(() -> new NoSuchElementException("Upgrade not found: " + upgradeId));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        String pkId = userId + "#" + upgradeId;
        PlayerUpgradeEntity pu = playerUpgradeRepository.findById(pkId)
                .orElseGet(() -> {
                    PlayerUpgradeEntity n = new PlayerUpgradeEntity();
                    n.setId(pkId);
                    n.setUserId(userId);
                    n.setUpgradeId(upgradeId);
                    n.setLevel(0);
                    n.setTotalSpent(0);
                    return n;
                });

        if (upgrade.getMaxLevel() > 0 && pu.getLevel() >= upgrade.getMaxLevel()) {
            throw new IllegalStateException("Upgrade already at max level");
        }

        double cost = nextCost(upgrade.getBaseCost(), pu.getLevel());
        if (user.getCookies() < cost) {
            throw new IllegalArgumentException("Nicht genug Cookies. Brauche " + cost + ", habe " + user.getCookies());
        }

        user.setCookies(user.getCookies() - cost);
        pu.setLevel(pu.getLevel() + 1);
        pu.setTotalSpent(pu.getTotalSpent() + cost);

        userRepository.save(user);
        playerUpgradeRepository.save(pu);

        return getUpgradesForPlayer(userId);
    }

    // Upgrade-Level eines Spielers abrufen (für andere Services)
    public int getLevel(String userId, String upgradeId) {
        return playerUpgradeRepository.findByUserIdAndUpgradeId(userId, upgradeId)
                .map(PlayerUpgradeEntity::getLevel)
                .orElse(0);
    }

    // cost(level) = baseCost × 1.15^level
    public static double nextCost(double baseCost, int currentLevel) {
        return Math.round(baseCost * Math.pow(1.15, currentLevel) * 100.0) / 100.0;
    }
}
