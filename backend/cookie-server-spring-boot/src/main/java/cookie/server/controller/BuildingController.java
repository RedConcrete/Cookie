package cookie.server.controller;

import cookie.server.dto.PlayerBuildingDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.dto.WageLedgerEntryDto;
import cookie.server.dto.WageStatusDto;
import cookie.server.entity.UserEntity;
import cookie.server.service.BuildingService;
import cookie.server.service.PassiveIncomeService;
import cookie.server.service.UserService;
import cookie.server.service.WageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/farm")
public class BuildingController {

    private final BuildingService buildingService;
    private final UserService userService;
    private final PassiveIncomeService passiveIncomeService;
    private final WageService wageService;

    public BuildingController(BuildingService buildingService, UserService userService,
                              PassiveIncomeService passiveIncomeService, WageService wageService) {
        this.buildingService = buildingService;
        this.userService = userService;
        this.passiveIncomeService = passiveIncomeService;
        this.wageService = wageService;
    }

    @GetMapping("/buildings/{userId}")
    public ResponseEntity<List<PlayerBuildingDto>> getBuildings(@PathVariable String userId) {
        return ResponseEntity.ok(buildingService.getBuildings(userId));
    }

    // Leichtgewichtiges Polling-Ziel fuers Frontend, um eine neue Lohnabbuchung zu erkennen
    // (fallende rote Zahl am Cookie-HUD, siehe FarmGridView.vue) -- absichtlich schlanker als
    // /game/init.
    @GetMapping("/wage-status/{userId}")
    public ResponseEntity<WageStatusDto> getWageStatus(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(userService.getWageStatus(userId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Abrechnungshistorie fürs Rathaus (Tab "Abrechnung") -- neueste zuerst, limit gedeckelt
    // auf balance.wageLedgerMaxEntries (siehe WageService#getWageHistory).
    @GetMapping("/wage-history/{userId}")
    public ResponseEntity<List<WageLedgerEntryDto>> getWageHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(wageService.getWageHistory(userId, limit));
    }

    @PostMapping("/buildings/buy/{userId}")
    public ResponseEntity<List<PlayerBuildingDto>> buyBuilding(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        String buildingId = body.get("buildingId");
        if (buildingId == null) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(buildingService.buyOrUpgrade(userId, buildingId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/citizens/buy/{userId}")
    public ResponseEntity<UserInformationDto> buyCitizens(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {
        int count = ((Number) body.getOrDefault("count", 1)).intValue();
        try {
            UserEntity updated = buildingService.buyCitizens(userId, count);
            UserInformationDto dto = userService.getUser(updated.getSteamId());
            dto.setTotalResourceCap(buildingService.getTotalCap(userId));
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Umkehrung von buyCitizens -- nur unbesetzte Buerger, Teil-Erstattung (siehe
    // BuildingService#fireCitizens). Fuer Spieler, die Lohnkosten senken wollen (z.B. um aus
    // dem Bankrott-Warnzustand rauszukommen, siehe FarmGridView#showBankruptcyWarning).
    @PostMapping("/citizens/fire/{userId}")
    public ResponseEntity<UserInformationDto> fireCitizens(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {
        int count = ((Number) body.getOrDefault("count", 1)).intValue();
        try {
            UserEntity updated = buildingService.fireCitizens(userId, count);
            UserInformationDto dto = userService.getUser(updated.getSteamId());
            dto.setTotalResourceCap(buildingService.getTotalCap(userId));
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Umkehrung von buyBuilding -- verkauft eine Stufe zurueck (siehe BuildingService#sellBuilding).
    @PostMapping("/buildings/sell/{userId}")
    public ResponseEntity<List<PlayerBuildingDto>> sellBuilding(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {
        String buildingId = body.get("buildingId");
        if (buildingId == null) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(buildingService.sellBuilding(userId, buildingId));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/buildings/workers/{userId}")
    public ResponseEntity<List<PlayerBuildingDto>> changeWorkers(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {
        String buildingId = (String) body.get("buildingId");
        int delta = ((Number) body.getOrDefault("delta", 0)).intValue();
        if (buildingId == null) return ResponseEntity.badRequest().build();
        try {
            return ResponseEntity.ok(buildingService.changeWorkers(userId, buildingId, delta));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Sammelt die im Gebäude angesammelte passive Produktion ein -- wie eine Miete, die
    // manuell abgeholt werden muss (siehe PassiveIncomeService#collectBuilding). Aufrufbar
    // sowohl über den Badge auf der Karte als auch über den Button im Gebäude-Dialog.
    @PostMapping("/buildings/collect/{userId}/{buildingId}")
    public ResponseEntity<UserInformationDto> collectBuilding(
            @PathVariable String userId,
            @PathVariable String buildingId) {
        try {
            return ResponseEntity.ok(passiveIncomeService.collectBuilding(userId, buildingId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
