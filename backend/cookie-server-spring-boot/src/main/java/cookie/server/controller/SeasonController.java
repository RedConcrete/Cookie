package cookie.server.controller;

import cookie.server.dto.SeasonDto;
import cookie.server.service.SeasonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService seasonService;

    public SeasonController(SeasonService seasonService) {
        this.seasonService = seasonService;
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrent() {
        Optional<SeasonDto> season = seasonService.getCurrentSeason();
        if (season.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(season.get());
    }
}
