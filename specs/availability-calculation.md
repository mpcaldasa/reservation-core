# Availability calculation

Slots are unique and ordered by their UTC start instant, even when weekly rules overlap. All time-based policy checks use the application `Clock`.

Resource capacity is measured by the peak number of reserved units at any instant, not the total number of bookings touching a candidate slot. Intervals are half-open: a booking ending at 10:00 does not overlap one starting at 10:00. For capacity two, adjacent bookings from 09:00–09:30 and 09:30–10:00 leave one unit available for a 09:00–10:00 booking. Creation and rescheduling use the same calculation; rescheduling excludes the booking being moved.

Booking writes retain the existing company transaction lock and the PostgreSQL exclusion constraint for capacity-one resources. The capacity calculation does not replace these concurrency controls.

Calendar ranges must be positive and at most exactly 31 days. Customer ownership is included in the database query; authorized staff retain company-wide visibility. Availability and calendar checks are covered by PostgreSQL Testcontainers tests in `BookingApiIntegrationTest`, including adjacent intervals, overlapping rules, a controlled clock, rescheduling, customer visibility and a range exceeding the limit by one nanosecond.
