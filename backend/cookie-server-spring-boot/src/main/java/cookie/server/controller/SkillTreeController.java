package cookie.server.controller;

import cookie.server.dto.AllocateNodeRequestDto;
import cookie.server.dto.ImportBuildRequestDto;
import cookie.server.dto.ImportBuildResultDto;
import cookie.server.dto.SkillTreeDto;
import cookie.server.service.SkillTreeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/skilltree")
public class SkillTreeController {

    private final SkillTreeService skillTreeService;

    public SkillTreeController(SkillTreeService skillTreeService) {
        this.skillTreeService = skillTreeService;
    }

    @GetMapping
    public ResponseEntity<SkillTreeDto> getTree(@RequestParam String userId) {
        return ResponseEntity.ok(skillTreeService.getTreeStatus(userId));
    }

    @PostMapping("/buy-point/{userId}")
    public ResponseEntity<SkillTreeDto> buyPoint(@PathVariable String userId) {
        return ResponseEntity.ok(skillTreeService.buySkillPoint(userId));
    }

    @PostMapping("/allocate/{userId}")
    public ResponseEntity<SkillTreeDto> allocate(
            @PathVariable String userId,
            @RequestBody AllocateNodeRequestDto request) {
        return ResponseEntity.ok(skillTreeService.allocateNode(userId, request.getNodeId()));
    }

    @PostMapping("/deallocate/{userId}")
    public ResponseEntity<SkillTreeDto> deallocate(
            @PathVariable String userId,
            @RequestBody AllocateNodeRequestDto request) {
        return ResponseEntity.ok(skillTreeService.deallocateNode(userId, request.getNodeId()));
    }

    // Spieler-Build-Sharing (Feature 2, siehe
    // docs/plans/2026-08-21-open-skillbaum-export-import-sharing.md): dryRun=true berechnet nur
    // Diff+Kosten (Vorschau vor der eigentlichen Bestaetigung), ohne etwas zu aendern.
    @PostMapping("/import-build/{userId}")
    public ResponseEntity<ImportBuildResultDto> importBuild(
            @PathVariable String userId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestBody ImportBuildRequestDto request) {
        return ResponseEntity.ok(skillTreeService.importBuild(userId, request.getNodeIds(), dryRun));
    }
}
