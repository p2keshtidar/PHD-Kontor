package de.frauas.girokonto.notification;

import de.frauas.girokonto.auth.JwtService;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Prüft die WebSocket-Verbindung (/F510/): Beim CONNECT muss ein gültiges Access-Token mitkommen,
 * abonniert werden darf nur die eigene Queue (/user/queue/…).
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final AppUserRepository users;

    public StompAuthInterceptor(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            if (accessor.getUser() == null || dest == null || !dest.startsWith("/user/queue/")) {
                throw new MessageDeliveryException("Abonnement nicht erlaubt");
            }
        }
        return message;
    }

    Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Token fehlt");
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            Integer version = claims.get(JwtService.CLAIM_VERSION, Integer.class);
            AppUser user = users.findById(userId)
                    .orElseThrow(() -> new MessageDeliveryException("Unbekannter Nutzer"));
            if (version == null || version != user.getTokenVersion()) {
                throw new MessageDeliveryException("Token ungültig");
            }
            String name = String.valueOf(userId);
            return () -> name;
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessageDeliveryException("Token ungültig");
        }
    }
}
