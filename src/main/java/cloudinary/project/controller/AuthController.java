package cloudinary.project.controller;

import cloudinary.project.dto.*;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        return ResponseEntity.ok(authService.login(loginRequestDto));
    }

    @PostMapping("/signup")
    public ResponseEntity<RegisterUserResponseDto> signup(@Valid @RequestBody RegisterUserRequestDto registerUserRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerUserRequestDto));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@AuthenticationPrincipal UserEntity currentUser) {
        boolean isLoggedOut = authService.getUserLoggedOut(currentUser);
        if(isLoggedOut)
            return ResponseEntity.ok("Logged out successfully");
        else
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
