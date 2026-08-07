package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.config.PlayerConfig;
import cookie.server.dto.SeasonDto;
import cookie.server.entity.UserEntity;
import cookie.server.repository.BakeJobRepository;
import cookie.server.repository.PlayerBuildingRepository;
import cookie.server.repository.PlayerSkillNodeRepository;
import cookie.server.repository.UserRepository;
import cookie.server.service.MarketService;
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
    private final PlayerSkillNodeRepository skillNodeRepository;
    private final PlayerBuildingRepository buildingRepository;
    private final BakeJobRepository bakeJobRepository;
    private final MarketService marketService;

    public AdminController(AppConfig appConfig, PlayerConfig playerConfig,
                           SeasonService seasonService, UserRepository userRepository,
                           PlayerSkillNodeRepository skillNodeRepository,
                           PlayerBuildingRepository buildingRepository,
                           BakeJobRepository bakeJobRepository,
                           MarketService marketService) {
        this.appConfig = appConfig;
        this.playerConfig = playerConfig;
        this.seasonService = seasonService;
        this.userRepository = userRepository;
        this.skillNodeRepository = skillNodeRepository;
        this.buildingRepository = buildingRepository;
        this.bakeJobRepository = bakeJobRepository;
        this.marketService = marketService;
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

        // Reset to the same starting values a brand-new account gets (see UserService) --
        // dev-reset used to hand DEV_PLAYER_001 a hardcoded 5000 cookies + 100 of every
        // resource here, bypassing PlayerConfig entirely, so testing "dev reset" never
        // actually reflected the real new-player experience/balance.
        double startCookies = playerConfig.getInitialCookies();
        user.setCookies(startCookies);
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
        userRepository.save(user);

        // Clear skill tree, buildings, bake jobs
        skillNodeRepository.deleteByUserId(userId);
        buildingRepository.deleteByUserId(userId);
        bakeJobRepository.deleteAll(bakeJobRepository.findAllByUserIdAndClaimedFalse(userId));

        return ResponseEntity.ok(Map.of("ok", true, "cookies", startCookies));
    }

    /** Setzt den Markt (Stock + Preise) auf die Ausgangswerte zurueck. In dev mode ohne Token. */
    @PostMapping("/market/reset")
    public ResponseEntity<?> resetMarket(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        marketService.resetMarket();
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
