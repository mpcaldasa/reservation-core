package com.slotix.reservationcore.booking;

import java.util.List;

public record BookingPageResponse(List<BookingResponse> items, int page, int size,
                                  long totalElements, int totalPages) {}
