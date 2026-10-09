package com.slotix.reservationcore.operational;

import com.slotix.reservationcore.audit.AuditPageResponse;
import com.slotix.reservationcore.audit.AuditQueryService;
import com.slotix.reservationcore.notification.NotificationDeliveryPageResponse;
import com.slotix.reservationcore.notification.NotificationDeliveryResponse;
import com.slotix.reservationcore.notification.NotificationOperationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/platform/operations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class OperationalController {
    private final AuditQueryService audit;
    private final NotificationOperationsService notifications;

    public OperationalController(AuditQueryService audit, NotificationOperationsService notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    @GetMapping("/audit")
    public AuditPageResponse audit(@RequestParam(required = false) UUID companyId,
                                   @RequestParam(required = false) String action,
                                   @RequestParam(required = false) String entityType,
                                   @RequestParam(required = false) UUID entityId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return audit.search(companyId, action, entityType, entityId, page, size);
    }

    @GetMapping("/notification-deliveries")
    public NotificationDeliveryPageResponse deliveries(@RequestParam(defaultValue = "FAILED") String status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return notifications.list(status, page, size);
    }

    @PostMapping("/notification-deliveries/{deliveryId}/replay")
    public NotificationDeliveryResponse replay(@PathVariable UUID deliveryId) {
        return notifications.replay(deliveryId);
    }
}
