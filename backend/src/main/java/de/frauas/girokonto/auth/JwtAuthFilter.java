package de.frauas.girokonto.auth;

import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Liest "Authorization: Bearer ..." aus, prüft das JWT und meldet den Nutzer für diese Anfrage an.
 * Ungültige Tokens werden ignoriert; geschützte Endpunkte antworten dann mit 401.
 * Wird bewusst nicht als @Component registriert, sondern nur in der SecurityConfig verwendet.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository users;

    public JwtAuthFilter(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                Integer version = claims.get(JwtService.CLAIM_VERSION, Integer.class);
                Optional<AppUser> user = users.findById(userId);
                // Nach einer Abmeldung ist die token_version erhöht -> altes Token gilt nicht mehr
                if (user.isPresent() && version != null && version == user.get().getTokenVersion()) {
                    AppUser u = user.get();
                    AuthUser principal = new AuthUser(u.getId(), u.getEmail(), u.getRole());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
