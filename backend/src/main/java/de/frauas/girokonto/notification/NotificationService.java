package de.frauas.girokonto.notification;

import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.PageDto;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Speichert Benachrichtigungen und schickt sie live per WebSocket an die App (/F510/, /F520/).
 *
 * Der Versand passiert erst NACH dem erfolgreichen Commit der Datenbanktransaktion.
 * So bekommt niemand eine Meldung über eine Buchung, die am Ende doch zurückgerollt wurde.
 */
@Service
public class NotificationService {

    /** Ziel im Client: /user/queue/notifications */
    public static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository notifications;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, SimpMessagingTemplate messaging, Clock clock) {
        this.notifications = notifications;
        this.messaging = messaging;
        this.clock = clock;
    }

    @Transactional
    public Notification send(Long userId, NotificationType type, String title, String body, Long bookingId) {
        Notification n = notifications.save(new Notification(userId, type, truncate(title, 100),
                truncate(body, 300), bookingId, Instant.now(clock)));
        NotificationDto dto = NotificationDto.from(n);
        Runnable send = () -> messaging.convertAndSendToUser(String.valueOf(userId), USER_QUEUE, dto);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
        return n;
    }

    @Transactional(readOnly = true)
    public PageDto<NotificationDto> list(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Notification> p = notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId,
                PageRequest.of(Math.max(page, 0), safeSize));
        return new PageDto<>(p.getContent().stream().map(NotificationDto::from).toList(),
                p.getNumber(), safeSize, p.hasNext());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "Benachrichtigung nicht gefunden."))
                .markRead();
    }

    @Transactional
    public void markAllRead(Long userId) {
        notifications.markAllRead(userId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
