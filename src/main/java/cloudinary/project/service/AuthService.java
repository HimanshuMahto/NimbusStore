package cloudinary.project.service;

import cloudinary.project.dto.*;
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

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        Authentication authenticate = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequestDto.getUsername(), loginRequestDto.getPassword())
        );
        if (!(authenticate.getPrincipal() instanceof UserEntity user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid principal");
        }
        String token = jwtService.generateAccessToken(user);
        return new LoginResponseDto(token, user.getId().toString());
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

    public boolean getUserLoggedOut(UserEntity currentUser) {
        // Stateless JWT: logout is handled client-side by discarding the token.
        // Server-side we just confirm the user is valid.
        if (currentUser == null || userRepository.findById(currentUser.getId()).isEmpty()) {
            return false;
        }
        return true;
    }

    public UserDto getUserData(UserEntity currentUser) {
        if(userRepository.findById(currentUser.getId()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        return new UserDto(currentUser.getId(), currentUser.getUsername(), currentUser.getEmail(), currentUser.getCreatedAt(), currentUser.getUpdatedAt());
    }

    public UserDto updateUserData(Map<String, Object> updatedValues, UserEntity currentUser) {
        if(userRepository.findById(currentUser.getId()).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        UserEntity user = userRepository.findById(currentUser.getId()).get();
        updatedValues.forEach((key, value) -> {
            if(key.equals("username")) user.setUsername((String) value);
            if(key.equals("email")) user.setEmail((String) value);
            }
        );
        userRepository.save(user);
        return new UserDto(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
