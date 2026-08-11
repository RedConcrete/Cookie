package cookie.server.controller;

import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.service.BuildingService;
import cookie.server.service.PlayerResetService;
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
    private final PlayerResetService playerResetService;

    public UserController(UserService userService, BuildingService buildingService,
                          PlayerResetService playerResetService) {
        this.userService = userService;
        this.buildingService = buildingService;
        this.playerResetService = playerResetService;
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

    // Self-service hard reset: bankruptcy confirmation and manual settings reset both call
    // this. Wipes the current run back to a fresh-account state -- see PlayerResetService.
    @PostMapping("/{userId}/hard-reset")
    public ResponseEntity<UserInformationDto> hardReset(@PathVariable String userId) {
        playerResetService.hardReset(userId);
        UserInformationDto dto = userService.getUser(userId);
        dto.setTotalResourceCap(buildingService.getTotalCap(userId));
        return ResponseEntity.ok(dto);
    }

    // Activity heartbeat while actually playing (see useIdleTimeout.js) -- keeps
    // WageScheduler from treating this player as AFK, see UserService#recordHeartbeat.
    @PostMapping("/{userId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String userId) {
        userService.recordHeartbeat(userId);
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
