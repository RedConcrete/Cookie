package cookie.server.controller;

import cookie.server.dto.LeaderboardEntryDto;
import cookie.server.dto.PlayerProfileDto;
import cookie.server.service.NetWorthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LeaderboardController {

    private final NetWorthService netWorthService;

    public LeaderboardController(NetWorthService netWorthService) {
        this.netWorthService = netWorthService;
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntryDto>> getLeaderboard() {
        return ResponseEntity.ok(netWorthService.getLeaderboard());
    }

    @GetMapping("/players/{steamId}/networth")
    public ResponseEntity<LeaderboardEntryDto> getNetWorth(@PathVariable String steamId) {
        return ResponseEntity.ok(netWorthService.getNetWorthForUser(steamId));
    }

    @GetMapping("/players/{steamId}/profile")
    public ResponseEntity<PlayerProfileDto> getProfile(@PathVariable String steamId) {
        return ResponseEntity.ok(netWorthService.getProfile(steamId));
    }
}
