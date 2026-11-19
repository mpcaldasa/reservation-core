package com.slotix.reservationcore.notification;

import java.util.List;

public record NotificationDeliveryPageResponse(List<NotificationDeliveryResponse> items, int page, int size,
                                               long totalElements, int totalPages) {}
