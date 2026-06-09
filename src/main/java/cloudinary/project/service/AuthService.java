package cloudinary.project.service;

import cloudinary.project.dto.LoginRequestDto;
import cloudinary.project.dto.LoginUserDto;
import cloudinary.project.dto.RegisterUserRequestDto;
import cloudinary.project.dto.RegisterUserResponseDto;
import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginUserDto login(LoginRequestDto loginRequestDto) {
        Authentication authenticate = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequestDto.getUsername(), loginRequestDto.getPassword())
        );
        if (!(authenticate.getPrincipal() instanceof UserEntity user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal");
        }
        String token = jwtService.generateAccessToken(user);
        return new LoginUserDto(token, user.getId().toString());
    }

    public RegisterUserResponseDto register(RegisterUserRequestDto registerUserRequestDto) {
        // Check if user already exists with username or email
        UserEntity user = userRepository
                .findByUsername(registerUserRequestDto.getUsername())
                .or(() -> userRepository.
                        findByEmail(registerUserRequestDto.getEmail()))
                .orElse(null);

        if (user != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
        }
        user = userRepository.save(UserEntity.builder()
                .username(registerUserRequestDto.getUsername())
                .email(registerUserRequestDto.getEmail())
                .passwordHashed(passwordEncoder.encode(registerUserRequestDto.getPassword()))
                .build()
        );
        return new RegisterUserResponseDto(user.getId(), user.getUsername(), user.getEmail());
    }
}
