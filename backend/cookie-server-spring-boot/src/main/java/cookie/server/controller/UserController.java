package cookie.server.controller;

import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.service.BuildingService;
import cookie.server.service.UserService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final BuildingService buildingService;

    public UserController(UserService userService, BuildingService buildingService) {
        this.userService = userService;
        this.buildingService = buildingService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<UserInformationDto> createUser(
            @PathVariable String userId,
            @RequestBody UserDto userDto) {

        UserInformationDto dto = userService.createUser(userId, userDto);
        dto.setTotalResourceCap(buildingService.getTotalCap(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInformationDto> getUser(@PathVariable String userId) {
        UserInformationDto dto = userService.getUser(userId);
        dto.setTotalResourceCap(buildingService.getTotalCap(userId));
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Serves the Steam avatar bytes cached server-side (see UserService#downloadAvatarIfNeeded)
    // instead of the client hotlinking Steam's CDN directly -- long cache header so the browser
    // only fetches it once.
    @GetMapping("/{userId}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable String userId) {
        return userService.getAvatarImage(userId)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.contentType() != null ? img.contentType() : "image/jpeg"))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(img.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
