package de.frauas.girokonto.user;

import de.frauas.girokonto.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserService userService;

    public MeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserDto me(@AuthenticationPrincipal AuthUser auth) {
        return UserDto.from(userService.get(auth.id()));
    }

    @PutMapping
    public UserDto update(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody UpdateProfileRequest req) {
        return UserDto.from(userService.updateProfile(auth.id(), req));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthUser auth,
                                               @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(auth.id(), req);
        return ResponseEntity.noContent().build();
    }
}
