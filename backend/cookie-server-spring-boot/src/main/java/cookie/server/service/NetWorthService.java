package cookie.server.service;

import cookie.server.dto.LeaderboardEntryDto;
import cookie.server.dto.NetWorthHistoryDto;
import cookie.server.dto.PlayerProfileDto;
import cookie.server.dto.UpgradeWithStatusDto;
import cookie.server.entity.MarketEntity;
import cookie.server.entity.NetWorthHistoryEntity;
import cookie.server.entity.UserEntity;
import cookie.server.repository.NetWorthHistoryRepository;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NetWorthService {

    private static final Logger log = LoggerFactory.getLogger(NetWorthService.class);

    private final UserRepository userRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final MarketService marketService;
    private final UpgradeService upgradeService;
    private final NetWorthHistoryRepository historyRepository;

    public NetWorthService(UserRepository userRepository,
                           PlayerUpgradeRepository playerUpgradeRepository,
                           MarketService marketService,
                           @Lazy UpgradeService upgradeService,
                           NetWorthHistoryRepository historyRepository) {
        this.userRepository = userRepository;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.marketService = marketService;
        this.upgradeService = upgradeService;
        this.historyRepository = historyRepository;
    }

    public LeaderboardEntryDto calculateForUser(UserEntity user, MarketEntity market) {
        double resourceValue =
            user.getSugar()     * market.getSugarPrice()     +
            user.getFlour()     * market.getFlourPrice()     +
            user.getEggs()      * market.getEggsPrice()      +
            user.getButter()    * market.getButterPrice()    +
            user.getChocolate() * market.getChocolatePrice() +
            user.getMilk()      * market.getMilkPrice();

        double upgradeValue = playerUpgradeRepository.findByUserId(user.getSteamId())
                .stream().mapToDouble(pu -> pu.getTotalSpent()).sum();

        LeaderboardEntryDto dto = new LeaderboardEntryDto();
        dto.setSteamId(user.getSteamId());
        dto.setCookies(user.getCookies());
        dto.setResourceValue(resourceValue);
        dto.setUpgradeValue(upgradeValue);
        dto.setNetWorth(user.getCookies() + resourceValue + upgradeValue);
        return dto;
    }

    // ── Scheduler: Net Worth alle 30s sichern ────────────────────
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void recordSnapshots() {
        try {
            MarketEntity market = marketService.getOrCreateCurrentMarket();
            List<UserEntity> users = userRepository.findAll();
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

            List<NetWorthHistoryEntity> entries = users.stream().map(user -> {
                LeaderboardEntryDto nw = calculateForUser(user, market);
                NetWorthHistoryEntity e = new NetWorthHistoryEntity();
                e.setId(UUID.randomUUID().toString());
                e.setUserId(user.getSteamId());
                e.setTimestamp(now);
                e.setNetWorth(nw.getNetWorth());
                e.setCookies(nw.getCookies());
                e.setResourceValue(nw.getResourceValue());
                e.setUpgradeValue(nw.getUpgradeValue());
                return e;
            }).collect(Collectors.toList());

            historyRepository.saveAll(entries);

            // Cleanup: rohe Daten älter als 48h löschen
            LocalDateTime cutoff = now.minusHours(48);
            users.forEach(u -> historyRepository.deleteOldByUserId(u.getSteamId(), cutoff));
        } catch (Exception e) {
            log.error("NetWorth snapshot failed: {}", e.getMessage());
        }
    }

    // ── Aggregierte History für einen Spieler ────────────────────
    public List<NetWorthHistoryDto> getHistory(String userId) {
        List<NetWorthHistoryEntity> raw = historyRepository.findByUserIdOrderByTimestampAsc(userId);
        if (raw.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff1h  = now.minusHours(1);
        LocalDateTime cutoff24h = now.minusHours(24);

        // < 1h: roh (alle 30s)
        List<NetWorthHistoryEntity> recent = raw.stream()
                .filter(e -> e.getTimestamp().isAfter(cutoff1h))
                .collect(Collectors.toList());

        // 1h–24h: 1 pro Minute
        List<NetWorthHistoryEntity> medium = aggregate(
                raw.stream().filter(e -> e.getTimestamp().isAfter(cutoff24h) && !e.getTimestamp().isAfter(cutoff1h)).collect(Collectors.toList()),
                ChronoUnit.MINUTES);

        // >24h: 1 pro Stunde
        List<NetWorthHistoryEntity> old = aggregate(
                raw.stream().filter(e -> !e.getTimestamp().isAfter(cutoff24h)).collect(Collectors.toList()),
                ChronoUnit.HOURS);

        List<NetWorthHistoryEntity> combined = new ArrayList<>();
        combined.addAll(old);
        combined.addAll(medium);
        combined.addAll(recent);
        return combined.stream().map(NetWorthHistoryDto::new).collect(Collectors.toList());
    }

    private List<NetWorthHistoryEntity> aggregate(List<NetWorthHistoryEntity> entries, ChronoUnit unit) {
        if (entries.isEmpty()) return List.of();
        Map<LocalDateTime, List<NetWorthHistoryEntity>> grouped = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getTimestamp().truncatedTo(unit)));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<NetWorthHistoryEntity> g = entry.getValue();
                    NetWorthHistoryEntity avg = new NetWorthHistoryEntity();
                    avg.setId("agg");
                    avg.setUserId(g.get(0).getUserId());
                    avg.setTimestamp(entry.getKey());
                    avg.setNetWorth(g.stream().mapToDouble(NetWorthHistoryEntity::getNetWorth).average().orElse(0));
                    avg.setCookies(g.stream().mapToDouble(NetWorthHistoryEntity::getCookies).average().orElse(0));
                    avg.setResourceValue(g.stream().mapToDouble(NetWorthHistoryEntity::getResourceValue).average().orElse(0));
                    avg.setUpgradeValue(g.stream().mapToDouble(NetWorthHistoryEntity::getUpgradeValue).average().orElse(0));
                    return avg;
                })
                .collect(Collectors.toList());
    }

    public List<LeaderboardEntryDto> getLeaderboard() {
        MarketEntity market = marketService.getOrCreateCurrentMarket();
        List<UserEntity> users = userRepository.findAll();
        List<LeaderboardEntryDto> ranked = users.stream()
                .map(u -> calculateForUser(u, market))
                .sorted(Comparator.comparingDouble(LeaderboardEntryDto::getNetWorth).reversed())
                .collect(Collectors.toList());
        java.util.concurrent.atomic.AtomicInteger rank = new java.util.concurrent.atomic.AtomicInteger(1);
        ranked.forEach(e -> e.setRank(rank.getAndIncrement()));
        return ranked;
    }

    public LeaderboardEntryDto getNetWorthForUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        MarketEntity market = marketService.getOrCreateCurrentMarket();
        LeaderboardEntryDto dto = calculateForUser(user, market);
        long rank = userRepository.findAll().stream()
                .map(u -> calculateForUser(u, market).getNetWorth())
                .filter(nw -> nw > dto.getNetWorth()).count() + 1;
        dto.setRank((int) rank);
        return dto;
    }

    public PlayerProfileDto getProfile(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        MarketEntity market = marketService.getOrCreateCurrentMarket();
        LeaderboardEntryDto nw = calculateForUser(user, market);
        long rank = userRepository.findAll().stream()
                .map(u -> calculateForUser(u, market).getNetWorth())
                .filter(v -> v > nw.getNetWorth()).count() + 1;
        List<UpgradeWithStatusDto> upgrades = upgradeService.getUpgradesForPlayer(userId);
        PlayerProfileDto dto = new PlayerProfileDto();
        dto.setSteamId(user.getSteamId());
        dto.setRank((int) rank);
        dto.setNetWorth(nw.getNetWorth());
        dto.setCookies(nw.getCookies());
        dto.setResourceValue(nw.getResourceValue());
        dto.setUpgradeValue(nw.getUpgradeValue());
        dto.setPrestigeLevel(user.getPrestigeLevel());
        dto.setLifetimeCookiesBaked(user.getLifetimeCookiesBaked());
        dto.setUpgrades(upgrades);
        return dto;
    }
}
