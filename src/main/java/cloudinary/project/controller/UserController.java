package cloudinary.project.controller;

import cloudinary.project.dto.UserDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getLoggedInUser(@AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.ok(authService.getUserData(currentUser));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateUserData(@RequestBody Map<String, Object> updateValues, @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.noContent().build();
    }

}
