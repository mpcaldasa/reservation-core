package com.slotix.reservationcore.identity;

import java.util.List;

public record MembershipPageResponse(List<MembershipResponse> items, int page, int size,
                                      long totalElements, int totalPages) {}
