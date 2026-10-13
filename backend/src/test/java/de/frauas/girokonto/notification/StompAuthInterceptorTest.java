package de.frauas.girokonto.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.frauas.girokonto.IntegrationTestBase;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** Anmeldung und Abo-Regeln der WebSocket-Verbindung (W01–W04). */
class StompAuthInterceptorTest extends IntegrationTestBase {

    @Autowired StompAuthInterceptor interceptor;

    private Message<byte[]> frame(StompCommand cmd, String auth, String dest, Principal user) {
        StompHeaderAccessor a = StompHeaderAccessor.create(cmd);
        if (auth != null) {
            a.addNativeHeader("Authorization", auth);
        }
        if (dest != null) {
            a.setDestination(dest);
        }
        if (user != null) {
            a.setUser(user);
        }
        a.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    @Test
    void W01_connectMitGueltigemTokenSetztNutzer() throws Exception {
        TestUser u = registerUser("Live");
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, u.bearer(), null, null), null);
        StompHeaderAccessor a = StompHeaderAccessor.wrap(out);
        assertThat(a.getUser()).isNotNull();
        assertThat(a.getUser().getName()).isEqualTo(String.valueOf(u.id()));
    }

    @Test
    void W02_connectOhneOderMitFalschemTokenAbgelehnt() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer kaputt", null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Basic abc", null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void W03_nachAbmeldenAltesTokenUngueltig() throws Exception {
        TestUser u = registerUser("Weg");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout")
                .header("Authorization", u.bearer()));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, u.bearer(), null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void W04_nurEigeneQueueAbonnieren() {
        Principal p = () -> "1";
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", p), null))
                .isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/queue/notifications", p), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", null), null))
                .isInstanceOf(MessageDeliveryException.class);
        // andere Befehle (z. B. Herzschlag) laufen durch
        assertThat(interceptor.preSend(frame(StompCommand.SEND, null, "/app/x", p), null)).isNotNull();
    }
}
