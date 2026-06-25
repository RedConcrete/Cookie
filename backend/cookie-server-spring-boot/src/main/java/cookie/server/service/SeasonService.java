package cookie.server.service;

import cookie.server.dto.LeaderboardEntryDto;
import cookie.server.dto.SeasonDto;
import cookie.server.dto.SeasonResultDto;
import cookie.server.entity.SeasonEntity;
import cookie.server.entity.SeasonResultEntity;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.SeasonRepository;
import cookie.server.repository.SeasonResultRepository;
import cookie.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    private final SeasonRepository seasonRepository;
    private final SeasonResultRepository seasonResultRepository;
    private final UserRepository userRepository;
    private final PlayerUpgradeRepository playerUpgradeRepository;
    private final BakeJobRepository bakeJobRepository;
    private final NetWorthService netWorthService;

    public SeasonService(SeasonRepository seasonRepository,
                         SeasonResultRepository seasonResultRepository,
                         UserRepository userRepository,
                         PlayerUpgradeRepository playerUpgradeRepository,
                         BakeJobRepository bakeJobRepository,
                         NetWorthService netWorthService) {
        this.seasonRepository = seasonRepository;
        this.seasonResultRepository = seasonResultRepository;
        this.userRepository = userRepository;
        this.playerUpgradeRepository = playerUpgradeRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.netWorthService = netWorthService;
    }

    public Optional<SeasonDto> getCurrentSeason() {
        return seasonRepository.findByActiveTrue().map(this::toDto);
    }

    public List<SeasonResultDto> getSeasonHistoryForUser(String userId) {
        List<SeasonResultEntity> results = seasonResultRepository.findByUserIdOrderBySeasonIdDesc(userId);
        Map<String, SeasonEntity> seasons = seasonRepository.findAll()
                .stream().collect(Collectors.toMap(SeasonEntity::getId, Function.identity()));

        return results.stream().map(r -> {
            SeasonResultDto dto = new SeasonResultDto();
            dto.setSeasonId(r.getSeasonId());
            SeasonEntity season = seasons.get(r.getSeasonId());
            dto.setSeasonName(season != null ? season.getName() : r.getSeasonId());
            dto.setFinalRank(r.getFinalRank());
            dto.setFinalNetWorth(r.getFinalNetWorth());
            dto.setPrestigeLevelAtEnd(r.getPrestigeLevelAtEnd());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public SeasonDto startNewSeason(String name) {
        // Close current season if one exists
        seasonRepository.findByActiveTrue().ifPresent(this::closeSeason);

        SeasonEntity season = new SeasonEntity();
        season.setId(UUID.randomUUID().toString());
        season.setName(name);
        season.setStartDate(LocalDateTime.now());
        season.setActive(true);
        seasonRepository.save(season);

        log.info("New season started: {} ({})", name, season.getId());
        SeasonDto dto = toDto(season);
        dto.setPlayerCount(0);
        return dto;
    }

    private void closeSeason(SeasonEntity season) {
        season.setActive(false);
        season.setEndDate(LocalDateTime.now());

        // Archive current leaderboard
        List<LeaderboardEntryDto> leaderboard = netWorthService.getLeaderboard();
        List<UserEntity> users = userRepository.findAll();
        Map<String, UserEntity> userMap = users.stream()
                .collect(Collectors.toMap(UserEntity::getSteamId, Function.identity()));

        List<SeasonResultEntity> results = leaderboard.stream().map(entry -> {
            SeasonResultEntity result = new SeasonResultEntity();
            result.setId(UUID.randomUUID().toString());
            result.setSeasonId(season.getId());
            result.setUserId(entry.getSteamId());
            result.setFinalNetWorth(entry.getNetWorth());
            result.setFinalRank(entry.getRank());
            UserEntity user = userMap.get(entry.getSteamId());
            result.setPrestigeLevelAtEnd(user != null ? user.getPrestigeLevel() : 0);
            return result;
        }).collect(Collectors.toList());

        seasonResultRepository.saveAll(results);
        seasonRepository.save(season);

        // Reset all players including prestige (Season-Reset wipes everything)
        users.forEach(user -> {
            user.setCookies(0);
            user.setSugar(0); user.setFlour(0); user.setEggs(0);
            user.setButter(0); user.setChocolate(0); user.setMilk(0);
            user.setPrestigeLevel(0);
            user.setTotalPrestiges(0);
        });
        userRepository.saveAll(users);

        playerUpgradeRepository.deleteAll();
        bakeJobRepository.deleteAll();

        log.info("Season closed: {} — {} players archived, all progress reset", season.getName(), results.size());
    }

    private SeasonDto toDto(SeasonEntity s) {
        int count = seasonResultRepository.findBySeasonId(s.getId()).size();
        SeasonDto dto = new SeasonDto();
        dto.setId(s.getId());
        dto.setName(s.getName());
        dto.setStartDate(s.getStartDate());
        dto.setEndDate(s.getEndDate());
        dto.setActive(s.isActive());
        dto.setPlayerCount(count);
        return dto;
    }
}
