package com.slotix.reservationcore.identity;

import java.util.List;

public record PlatformUserPageResponse(List<PlatformUserResponse> items, int page, int size,
                                       long totalElements, int totalPages) {}
