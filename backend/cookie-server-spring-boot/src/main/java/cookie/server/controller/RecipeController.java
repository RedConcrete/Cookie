package cookie.server.controller;

import cookie.server.dto.RecipeDto;
import cookie.server.service.BakeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final BakeService bakeService;

    public RecipeController(BakeService bakeService) {
        this.bakeService = bakeService;
    }

    @GetMapping
    public ResponseEntity<List<RecipeDto>> getRecipes() {
        return ResponseEntity.ok(bakeService.getRecipes());
    }
}
