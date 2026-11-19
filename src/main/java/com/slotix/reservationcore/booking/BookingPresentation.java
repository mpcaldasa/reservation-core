package com.slotix.reservationcore.booking;

import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import com.slotix.reservationcore.resource.Resource;
import com.slotix.reservationcore.resource.ResourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingPresentation {
    private final UserRepository users;
    private final ResourceRepository resources;

    public BookingPresentation(UserRepository users, ResourceRepository resources) {
        this.users = users;
        this.resources = resources;
    }

    public BookingResponse present(Booking booking) {
        return present(List.of(booking)).getFirst();
    }

    public List<BookingResponse> present(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();
        Map<UUID, User> customers = users.findAllById(bookings.stream().map(Booking::getCustomerUserId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Resource> catalog = resources.findAllById(bookings.stream().map(Booking::getResourceId).distinct().toList())
            .stream().collect(Collectors.toMap(Resource::getId, Function.identity()));
        return bookings.stream().map(booking -> {
            User customer = customers.get(booking.getCustomerUserId());
            Resource resource = catalog.get(booking.getResourceId());
            return BookingResponse.from(booking, customer == null ? null : customer.getFullName(),
                resource == null || !resource.getCompanyId().equals(booking.getCompanyId()) ? null : resource.getName());
        }).toList();
    }
}
