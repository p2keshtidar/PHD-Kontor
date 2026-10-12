package de.frauas.girokonto.notification;

import de.frauas.girokonto.auth.AuthUser;
import de.frauas.girokonto.common.PageDto;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageDto<NotificationDto> list(@AuthenticationPrincipal AuthUser auth,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(auth.id(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthUser auth) {
        return Map.of("count", notificationService.unreadCount(auth.id()));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthUser auth) {
        notificationService.markAllRead(auth.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal AuthUser auth, @PathVariable Long id) {
        notificationService.markRead(auth.id(), id);
        return ResponseEntity.noContent().build();
    }
}
