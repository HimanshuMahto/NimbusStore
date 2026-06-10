package cloudinary.project.controller;

import cloudinary.project.dto.LoginRequestDto;
import cloudinary.project.dto.LoginResponseDto;
import cloudinary.project.dto.RegisterUserRequestDto;
import cloudinary.project.dto.RegisterUserResponseDto;
import cloudinary.project.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
