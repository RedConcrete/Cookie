package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.config.PlayerConfig;
import cookie.server.dto.SeasonDto;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.PlayerUpgradeRepository;
import cookie.server.repository.UserRepository;
import cookie.server.service.SeasonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AppConfig appConfig;
    private final PlayerConfig playerConfig;
    private final SeasonService seasonService;
    private final UserRepository userRepository;
    private final PlayerUpgradeRepository upgradeRepository;
    private final PlayerBuildingRepository buildingRepository;
    private final BakeJobRepository bakeJobRepository;

    public AdminController(AppConfig appConfig, PlayerConfig playerConfig,
                           SeasonService seasonService, UserRepository userRepository,
                           PlayerUpgradeRepository upgradeRepository,
                           PlayerBuildingRepository buildingRepository,
                           BakeJobRepository bakeJobRepository) {
        this.appConfig = appConfig;
        this.playerConfig = playerConfig;
        this.seasonService = seasonService;
        this.userRepository = userRepository;
        this.upgradeRepository = upgradeRepository;
        this.buildingRepository = buildingRepository;
        this.bakeJobRepository = bakeJobRepository;
    }

    private boolean badToken(String token) {
        return !appConfig.getAdminToken().equals(token);
    }

    @PostMapping("/season/start")
    public ResponseEntity<?> startSeason(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        if (badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        String name = body.getOrDefault("name", "Unnamed Season");
        SeasonDto result = seasonService.startNewSeason(name);
        return ResponseEntity.ok(result);
    }

    /** Reset a player back to initial values. In dev mode no token required if userId == DEV_PLAYER_001. */
    @Transactional
    @PostMapping("/reset/{userId}")
    public ResponseEntity<?> resetPlayer(
            @PathVariable String userId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        boolean isDev = appConfig.isDevMode() && "DEV_PLAYER_001".equals(userId);
        if (!isDev && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        // Reset resources and cookies (give dev player plenty for testing)
        double startCookies = isDev ? 5000.0 : playerConfig.getInitialCookies();
        user.setCookies(startCookies);
        user.setSugar(isDev ? 100 : playerConfig.getInitialSugar());
        user.setFlour(isDev ? 100 : playerConfig.getInitialFlour());
        user.setEggs(isDev ? 100 : playerConfig.getInitialEggs());
        user.setButter(isDev ? 100 : playerConfig.getInitialButter());
        user.setChocolate(isDev ? 100 : playerConfig.getInitialChocolate());
        user.setMilk(isDev ? 100 : playerConfig.getInitialMilk());
        user.setWorkersIdle(false);
        user.setOwnedCitizens(0);
        userRepository.save(user);

        // Clear upgrades, buildings, bake jobs
        upgradeRepository.deleteAll(upgradeRepository.findByUserId(userId));
        buildingRepository.deleteByUserId(userId);
        bakeJobRepository.deleteAll(bakeJobRepository.findAllByUserIdAndClaimedFalse(userId));

        return ResponseEntity.ok(Map.of("ok", true, "cookies", startCookies));
    }
}
