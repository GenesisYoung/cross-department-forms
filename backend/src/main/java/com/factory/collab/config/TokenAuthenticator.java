package com.factory.collab.config;

import com.factory.collab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Turns a bearer token into an authenticated Spring Security token. Shared by HTTP and STOMP. */
@Component
@RequiredArgsConstructor
public class TokenAuthenticator {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public Optional<UsernamePasswordAuthenticationToken> authenticate(String headerOrToken) {
        if (headerOrToken == null || headerOrToken.isBlank()) return Optional.empty();
        String token = headerOrToken.startsWith("Bearer ") ? headerOrToken.substring(7) : headerOrToken;
        return jwtService.verify(token)
                .flatMap(userRepository::findByUsername)
                .filter(u -> u.isEnabled())
                .map(u -> {
                    AuthUser principal = AuthUser.of(u);
                    return new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                });
    }
}
