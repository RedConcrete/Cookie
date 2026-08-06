package cookie.server.controller;

import cookie.server.dto.AllocateNodeRequestDto;
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
}
