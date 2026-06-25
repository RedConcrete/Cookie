package cookie.server.controller;

import cookie.server.config.AppConfig;
import cookie.server.dto.SeasonDto;
import cookie.server.service.SeasonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AppConfig appConfig;
    private final SeasonService seasonService;

    public AdminController(AppConfig appConfig, SeasonService seasonService) {
        this.appConfig = appConfig;
        this.seasonService = seasonService;
    }

    @PostMapping("/season/start")
    public ResponseEntity<?> startSeason(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, String> body) {

        if (!appConfig.getAdminToken().equals(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid admin token");
        }

        String name = body.getOrDefault("name", "Unnamed Season");
        SeasonDto result = seasonService.startNewSeason(name);
        return ResponseEntity.ok(result);
    }
}
