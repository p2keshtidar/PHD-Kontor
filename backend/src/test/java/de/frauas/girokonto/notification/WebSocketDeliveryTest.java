package de.frauas.girokonto.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import de.frauas.girokonto.IntegrationTestBase;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Echte Live-Benachrichtigung über einen echten Server-Port (W05, W06). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketDeliveryTest extends IntegrationTestBase {

    @LocalServerPort int port;

    private StompSession connect(String bearer) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connect = new StompHeaders();
        if (bearer != null) {
            connect.add("Authorization", bearer);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
    }

    @Test
    void W05_ueberweisungKommtLiveAn() throws Exception {
        TestUser empfaenger = registerUser("Livia");
        TestUser sender = registerUser("Sven");
        StompSession session = connect(empfaenger.bearer());
        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(500);   // Abo ist aktiv

        mvc.perform(post("/api/transfers").header("Authorization", sender.bearer())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"" + empfaenger.email() + "\",\"amountCents\":4200,\"description\":\"Pizza\"}"));

        Map<String, Object> msg = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(msg).as("Live-Meldung innerhalb von 10 s").isNotNull();
        assertThat(msg.get("title")).isEqualTo("Geld erhalten");
        assertThat((String) msg.get("body")).contains("42,00").contains("Pizza");
        // Der Sender bekommt seine Meldung NICHT in die Queue des Empfängers
        assertThat(inbox.poll(1, TimeUnit.SECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void W06_verbindungOhneTokenScheitert() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(Exception.class);   // Fehler oder Zeitüberschreitung
    }
}
