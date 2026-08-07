package cookie.server.controller;

import cookie.server.dto.PlayerBuildingDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.entity.UserEntity;
import cookie.server.service.BuildingService;
import cookie.server.service.PassiveIncomeService;
import cookie.server.service.UserService;
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

    public BuildingController(BuildingService buildingService, UserService userService,
                              PassiveIncomeService passiveIncomeService) {
        this.buildingService = buildingService;
        this.userService = userService;
        this.passiveIncomeService = passiveIncomeService;
    }

    @GetMapping("/buildings/{userId}")
    public ResponseEntity<List<PlayerBuildingDto>> getBuildings(@PathVariable String userId) {
        return ResponseEntity.ok(buildingService.getBuildings(userId));
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
