package com.slotix.reservationcore.customer;

import java.util.List;

public record CustomerPageResponse(List<CustomerResponse> items, int page, int size,
                                    long totalElements, int totalPages) {}
