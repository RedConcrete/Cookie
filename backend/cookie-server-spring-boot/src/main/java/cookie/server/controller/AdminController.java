package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.dto.SeasonDto;
import cookie.server.service.MarketService;
import cookie.server.service.SeasonService;
import cookie.server.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AppConfig appConfig;
    private final SeasonService seasonService;
    private final MarketService marketService;
    private final UserService userService;

    public AdminController(AppConfig appConfig, SeasonService seasonService, MarketService marketService, UserService userService) {
        this.appConfig = appConfig;
        this.seasonService = seasonService;
        this.marketService = marketService;
        this.userService = userService;
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

    /** Setzt den Markt (Stock + Preise) auf die Ausgangswerte zurueck. In dev mode ohne Token. */
    @PostMapping("/market/reset")
    public ResponseEntity<?> resetMarket(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        marketService.resetMarket();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Testhilfe fuer den Bankrott-Screen (BankruptcyScreen.vue): setzt Cookies tief ins Minus,
     * sodass NetWorth garantiert negativ ist und der naechste Wage-Poll den Screen echt ueber
     * isBankrupt ausloest -- kein Fake-Trigger im Frontend. Nur in dev mode ohne Token.
     */
    @PostMapping("/users/{userId}/force-bankrupt")
    public ResponseEntity<?> forceBankrupt(
            @PathVariable String userId,
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!appConfig.isDevMode() && badToken(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        userService.forceBankrupt(userId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
