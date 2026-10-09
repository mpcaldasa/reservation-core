package com.slotix.reservationcore.audit;

import java.util.List;

public record AuditPageResponse(List<AuditEntryResponse> items, int page, int size, long totalElements,
                                int totalPages) {}
