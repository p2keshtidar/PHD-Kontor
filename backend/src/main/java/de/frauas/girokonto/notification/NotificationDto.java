package de.frauas.girokonto.notification;

import java.time.Instant;

public record NotificationDto(Long id, NotificationType type, String title, String body,
                              Long bookingId, boolean read, Instant createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getBookingId(), n.isRead(), n.getCreatedAt());
    }
}
