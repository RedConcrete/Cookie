package cookie.server.controller;

import cookie.server.dto.UserDto;
import cookie.server.dto.UserInformationDto;
import cookie.server.service.UserService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<UserInformationDto> createUser(
            @PathVariable String userId,
            @RequestBody UserDto userDto) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                userService.createUser(userId, userDto)
        );
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInformationDto> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
