package cookie.server.service;

import cookie.server.config.GameBalanceConfig;
import cookie.server.dto.PlayerBuildingDto;
import cookie.server.entity.PlayerBuildingEntity;
import cookie.server.entity.UserEntity;
import cookie.server.enums.ResourceName;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BuildingService {

    record BuildingDef(
        String id, String name, int baseCost, double wagePerMin,
        boolean upgradeable, boolean preBuilt, int maxWorkers,
        double passiveRatePerSecPerWorker, ResourceName passiveResource
    ) {}

    // Pre-built (ofen, rathaus, lager, markt) start at level 1 for every player.
    // Alle Produktionsgebäude sind jetzt upgradeable -- höhere Stufe = mehr Arbeiter-Slots,
    // Kosten steigen exponentiell (computeCost: baseCost × 2^level). maxWorkers hier ist die
    // Basis-Kapazität bei Stufe 1, siehe effectiveMaxWorkers().
    private static final List<BuildingDef> BUILDINGS = List.of(
        new BuildingDef("pond",    "Zuckerteich", 500, 4.0, true, false, 2, 0.7,  ResourceName.SUGAR),
        new BuildingDef("hof",     "Bauernhof",   300, 6.0, true, false, 3, 0.7,  ResourceName.FLOUR),
        new BuildingDef("huhn",    "Hühnerhof",   350, 4.0, true, false, 2, 0.4,  ResourceName.EGGS),
        new BuildingDef("butter",  "Butterei",    280, 2.0, true, false, 1, 0.6,  ResourceName.BUTTER),
        new BuildingDef("kakao",   "Plantage",    380, 4.0, true, false, 2, 0.6,  ResourceName.CHOCOLATE),
        new BuildingDef("kuh",     "Kuhstall",    600, 8.0, true, false, 4, 1.2,  ResourceName.MILK),
        new BuildingDef("ofen",    "Backhaus",    0,   0.0, false, true,  0, 0.0,  null),
        new BuildingDef("rathaus", "Rathaus",     400, 0.0, true,  true,  0, 0.0,  null),
        new BuildingDef("markt",   "Markt",       400, 0.0, true,  true,  0, 0.0,  null),
        new BuildingDef("lager",   "Lager",       400, 3.0, true,  true,  0, 0.0,  null)
    );

    private static final Map<String, BuildingDef> DEF_MAP;
    static {
        Map<String, BuildingDef> m = new LinkedHashMap<>();
        BUILDINGS.forEach(b -> m.put(b.id(), b));
        DEF_MAP = Collections.unmodifiableMap(m);
    }

    private final PlayerBuildingRepository buildingRepo;
    private final UserRepository userRepo;
    private final GameBalanceConfig balance;

    public BuildingService(PlayerBuildingRepository buildingRepo, UserRepository userRepo, GameBalanceConfig balance) {
        this.buildingRepo = buildingRepo;
        this.userRepo = userRepo;
        this.balance = balance;
    }

    public static Map<String, BuildingDef> getDefMap() { return DEF_MAP; }

    /** Creates level-1 entries for pre-built buildings if they don't exist yet. */
    @Transactional
    public void ensurePreBuiltBuildings(String userId) {
        Map<String, PlayerBuildingEntity> owned = ownedMap(userId);
        for (BuildingDef def : BUILDINGS) {
            if (!def.preBuilt() || owned.containsKey(def.id())) continue;
            PlayerBuildingEntity ent = new PlayerBuildingEntity();
            ent.setId(UUID.randomUUID().toString());
            ent.setUserId(userId);
            ent.setBuildingId(def.id());
            ent.setLevel(1);
            ent.setWorkers(0);
            buildingRepo.save(ent);
        }
    }

    public List<PlayerBuildingDto> getBuildings(String userId) {
        Map<String, PlayerBuildingEntity> owned = ownedMap(userId);
        return BUILDINGS.stream().map(def -> toDto(def, owned.get(def.id()))).toList();
    }

    public int getBuildingLevel(String userId, String buildingId) {
        return buildingRepo.findByUserIdAndBuildingId(userId, buildingId)
                .map(PlayerBuildingEntity::getLevel).orElse(0);
    }

    @Transactional
    public List<PlayerBuildingDto> buyOrUpgrade(String userId, String buildingId) {
        BuildingDef def = requireDef(buildingId);
        UserEntity user = requireUser(userId);
        PlayerBuildingEntity ent = buildingRepo.findByUserIdAndBuildingId(userId, buildingId).orElse(null);
        int currentLevel = ent != null ? ent.getLevel() : 0;

        // Pre-built at level 0 would be an error state; production buildings can only go 0→1
        if (!def.upgradeable() && currentLevel >= 1)
            throw new IllegalStateException("Building already owned: " + buildingId);

        double cost = computeCost(def, currentLevel);
        if (cost > 0 && user.getCookies() < cost)
            throw new IllegalStateException("Not enough cookies. Need " + cost);

        if (cost > 0) {
            user.setCookies(user.getCookies() - cost);
            userRepo.save(user);
        }

        if (ent == null) {
            ent = new PlayerBuildingEntity();
            ent.setId(UUID.randomUUID().toString());
            ent.setUserId(userId);
            ent.setBuildingId(buildingId);
            ent.setWorkers(0);
        }
        ent.setLevel(currentLevel + 1);
        buildingRepo.save(ent);
        return getBuildings(userId);
    }

    @Transactional
    public List<PlayerBuildingDto> changeWorkers(String userId, String buildingId, int delta) {
        BuildingDef def = requireDef(buildingId);
        PlayerBuildingEntity ent = buildingRepo.findByUserIdAndBuildingId(userId, buildingId)
                .orElseThrow(() -> new IllegalStateException("Building not owned"));
        if (delta > 0) {
            UserEntity user = requireUser(userId);
            int available = user.getOwnedCitizens() - getAssignedCitizens(userId);
            if (available <= 0) throw new IllegalStateException("No available citizens");
        }
        int newCount = Math.max(0, Math.min(effectiveMaxWorkers(def, ent.getLevel()), ent.getWorkers() + delta));
        ent.setWorkers(newCount);
        buildingRepo.save(ent);
        return getBuildings(userId);
    }

    @Transactional
    public UserEntity buyCitizens(String userId, int count) {
        int ratLevel = getBuildingLevel(userId, "rathaus");
        if (ratLevel == 0) throw new IllegalStateException("Rathaus not built");
        UserEntity user = requireUser(userId);
        int maxCitizens = ratLevel * balance.getCitizensPerRatLevel();
        int canBuy = maxCitizens - user.getOwnedCitizens();
        if (canBuy <= 0) throw new IllegalStateException("Max citizens reached (" + maxCitizens + ")");
        int actualBuy = Math.min(count, canBuy);

        double cost = 0;
        for (int i = 0; i < actualBuy; i++) {
            cost += citizenCost(user.getOwnedCitizens() + i);
        }
        if (user.getCookies() < cost) throw new IllegalStateException("Not enough cookies. Need " + cost);
        user.setCookies(user.getCookies() - cost);
        user.setOwnedCitizens(user.getOwnedCitizens() + actualBuy);
        return userRepo.save(user);
    }

    /** Kosten für den (ownedCount+1)-ten Bürger -- exponentiell, Wachstumsrate wie bei den Upgrades (1.15^n), nicht wie Gebäude-Ausbau (2^level). */
    private double citizenCost(int ownedCount) {
        return balance.getCitizenBaseCost() * Math.pow(balance.getCitizenCostGrowth(), ownedCount);
    }

    /** Arbeiter-Kapazität eines Gebäudes bei gegebener Stufe: Basis + (Stufe-1) zusätzliche Slots. */
    private int effectiveMaxWorkers(BuildingDef def, int level) {
        if (level <= 0) return 0;
        return def.maxWorkers() + (level - 1) * balance.getWorkersPerLevel();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public double getTotalCap(String userId) {
        return getTotalCap(ownedMap(userId));
    }

    /** Overload fuer Aufrufer, die die Gebaeude-Map schon geladen haben (z.B. pro Tick im Scheduler). */
    public double getTotalCap(Map<String, PlayerBuildingEntity> owned) {
        int lagerLevel = owned.containsKey("lager") ? owned.get("lager").getLevel() : 0;
        return balance.getBaseStorageCap() + lagerLevel * balance.getStoragePerLevel();
    }

    public double getTotalWage(String userId) {
        Map<String, PlayerBuildingEntity> owned = ownedMap(userId);
        return BUILDINGS.stream()
                .filter(def -> owned.containsKey(def.id()))
                .mapToDouble(def -> effectiveWage(def, owned.get(def.id())))
                .sum();
    }

    private double effectiveWage(BuildingDef def, PlayerBuildingEntity ent) {
        if (ent == null) return 0;
        // Lager: free at level 1, +wagePerMin for each level above 1
        if (def.id().equals("lager")) return Math.max(0, ent.getLevel() - 1) * def.wagePerMin();
        return def.wagePerMin();
    }

    public int getMaxCitizens(String userId) {
        return getBuildingLevel(userId, "rathaus") * balance.getCitizensPerRatLevel();
    }

    public int getAssignedCitizens(String userId) {
        return buildingRepo.findByUserId(userId).stream()
                .mapToInt(PlayerBuildingEntity::getWorkers).sum();
    }

    /** Per-player sell fee rate accounting for Markt upgrade level. */
    public double getEffectiveSellFeeRate(String userId, double baseRate) {
        return getEffectiveSellFeeRate(ownedMap(userId), baseRate);
    }

    /** Overload fuer Aufrufer, die die Gebaeude-Map schon geladen haben. */
    public double getEffectiveSellFeeRate(Map<String, PlayerBuildingEntity> owned, double baseRate) {
        int marktLevel = owned.containsKey("markt") ? owned.get("markt").getLevel() : 0;
        double discount = Math.max(0, marktLevel - 1) * 0.02;
        return Math.max(0.01, baseRate - discount);
    }

    public List<PassiveTick> computePassiveTicks(String userId, double tickSeconds) {
        return computePassiveTicks(ownedMap(userId), tickSeconds);
    }

    /** Overload fuer Aufrufer, die die Gebaeude-Map schon geladen haben. */
    public List<PassiveTick> computePassiveTicks(Map<String, PlayerBuildingEntity> owned, double tickSeconds) {
        List<PassiveTick> result = new ArrayList<>();
        for (BuildingDef def : BUILDINGS) {
            if (def.passiveResource() == null || def.passiveRatePerSecPerWorker() <= 0) continue;
            PlayerBuildingEntity ent = owned.get(def.id());
            if (ent == null || ent.getWorkers() <= 0) continue;
            double amount = def.passiveRatePerSecPerWorker() * ent.getWorkers() * tickSeconds;
            result.add(new PassiveTick(def.passiveResource(), amount));
        }
        return result;
    }

    public record PassiveTick(ResourceName resource, double amount) {}

    private double computeCost(BuildingDef def, int currentLevel) {
        if (def.baseCost() == 0) return 0;
        return def.baseCost() * Math.pow(balance.getBuildingCostGrowth(), currentLevel);
    }

    private PlayerBuildingDto toDto(BuildingDef def, PlayerBuildingEntity ent) {
        int level   = ent != null ? ent.getLevel() : 0;
        int workers = ent != null ? ent.getWorkers() : 0;
        double rate = def.passiveRatePerSecPerWorker() * workers * balance.getPassiveTickSeconds();

        PlayerBuildingDto dto = new PlayerBuildingDto();
        dto.setId(def.id());
        dto.setName(def.name());
        dto.setLevel(level);
        dto.setWagePerMin(effectiveWage(def, ent));
        dto.setStorageCapBonus(def.upgradeable() && def.id().equals("lager") ? (int) balance.getStoragePerLevel() : 0);
        dto.setCanUpgrade(def.upgradeable());
        dto.setNextLevelCost(def.upgradeable() || level == 0 ? computeCost(def, level) : 0);
        dto.setWorkers(workers);
        dto.setMaxWorkers(effectiveMaxWorkers(def, level));
        dto.setPassiveRatePerTick(rate);
        dto.setPreBuilt(def.preBuilt());
        return dto;
    }

    /** Alle Gebaeude eines Spielers als Map (buildingId -> Entity), einmal laden statt pro Helper neu zu queryen. */
    public Map<String, PlayerBuildingEntity> ownedMap(String userId) {
        Map<String, PlayerBuildingEntity> m = new HashMap<>();
        buildingRepo.findByUserId(userId).forEach(b -> m.put(b.getBuildingId(), b));
        return m;
    }

    private BuildingDef requireDef(String id) {
        BuildingDef def = DEF_MAP.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown building: " + id);
        return def;
    }

    private UserEntity requireUser(String userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}
