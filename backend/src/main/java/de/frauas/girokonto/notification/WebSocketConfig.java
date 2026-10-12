package de.frauas.girokonto.notification;

import de.frauas.girokonto.auth.JwtService;
import de.frauas.girokonto.config.AppProperties;
import de.frauas.girokonto.user.AppUser;
import de.frauas.girokonto.user.AppUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live-Benachrichtigungen über WebSocket mit dem STOMP-Protokoll (/F510/).
 *
 * Die App verbindet sich mit ws://host:8080/ws und schickt beim CONNECT das Access-Token
 * im Header "Authorization". Danach abonniert sie /user/queue/notifications und bekommt
 * nur ihre eigenen Meldungen.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final AppUserRepository users;
    private final AppProperties props;

    public WebSocketConfig(JwtService jwtService, AppUserRepository users, AppProperties props) {
        this.jwtService = jwtService;
        this.users = users;
        this.props = props;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(props.cors().allowedOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    // Nur die eigene Queue darf abonniert werden
                    String dest = accessor.getDestination();
                    if (accessor.getUser() == null || dest == null || !dest.startsWith("/user/queue/")) {
                        throw new MessageDeliveryException("Abonnement nicht erlaubt");
                    }
                }
                return message;
            }
        });
    }

    private Principal authenticate(String header) {
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
