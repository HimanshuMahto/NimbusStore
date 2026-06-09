package cloudinary.project.security;


import cloudinary.project.entity.UserEntity;
import cloudinary.project.repository.UserRepository;
import cloudinary.project.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try
            {
                log.info("JWT Filter: {}", request.getRequestURI());

                final String authorizationHeader = request.getHeader("Authorization");
                if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String token = authorizationHeader.split("Bearer ")[1];
                String username = jwtService.getUsernameFromToken(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserEntity userEntity = userRepository.findByUsername(username).orElse(null);
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userEntity, null, userEntity.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
                filterChain.doFilter(request, response);
            }
        catch (Exception e) {
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }
}
