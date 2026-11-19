package com.slotix.reservationcore.customer;

import com.slotix.reservationcore.audit.AuditService;
import com.slotix.reservationcore.booking.BookingPageResponse;
import com.slotix.reservationcore.booking.BookingPresentation;
import com.slotix.reservationcore.booking.BookingRepository;
import com.slotix.reservationcore.common.ApiErrorCode;
import com.slotix.reservationcore.common.ApiException;
import com.slotix.reservationcore.common.TenantAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','BOOKING_MANAGER')")
public class CustomerDirectoryService {
    private static final String FROM = """
        FROM company_memberships m JOIN users u ON u.id=m.user_id
        LEFT JOIN company_customer_profiles p ON p.company_id=m.company_id AND p.user_id=m.user_id
        WHERE m.company_id=? AND m.deleted_at IS NULL AND u.deleted_at IS NULL
        """;
    private static final String COLUMNS = """
        SELECT u.id,u.full_name,u.email,coalesce(p.phone,'') phone,coalesce(p.notes,'') notes,
               (m.status='ACTIVE' AND u.status='ACTIVE') active
        """;
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final BookingRepository bookings;
    private final BookingPresentation presentation;
    private final AuditService audit;

    public CustomerDirectoryService(JdbcTemplate jdbc, TenantAccessService access, BookingRepository bookings,
                                    BookingPresentation presentation, AuditService audit) {
        this.jdbc = jdbc;
        this.access = access;
        this.bookings = bookings;
        this.presentation = presentation;
        this.audit = audit;
    }

    public CustomerPageResponse search(UUID companyId, String query, boolean activeOnly, int page, int size) {
        access.requireActiveMembership(companyId);
        validatePage(page, size);
        if (query == null || query.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, "Search must not exceed 120 characters");
        }
        String text = "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        final String filters = " AND (NOT ? OR (m.status='ACTIVE' AND u.status='ACTIVE'))"
            + " AND (u.full_name ILIKE ? OR u.email ILIKE ? OR coalesce(p.phone,'') ILIKE ?)";
        Long count = jdbc.queryForObject("SELECT count(*) " + FROM + filters, Long.class,
            companyId, activeOnly, text, text, text);
        var items = jdbc.query(COLUMNS + FROM + filters + " ORDER BY lower(u.full_name),u.id LIMIT ? OFFSET ?",
            (row, index) -> new CustomerResponse(row.getObject("id", UUID.class), row.getString("full_name"),
                row.getString("email"), row.getString("phone"), row.getString("notes"), row.getBoolean("active")),
            companyId, activeOnly, text, text, text, size, (long) page * size);
        long total = count == null ? 0 : count;
        return new CustomerPageResponse(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public CustomerResponse get(UUID companyId, UUID userId) {
        access.requireActiveMembership(companyId);
        return jdbc.query(COLUMNS + FROM + " AND u.id=?", (row, index) ->
            new CustomerResponse(row.getObject("id", UUID.class), row.getString("full_name"), row.getString("email"),
                row.getString("phone"), row.getString("notes"), row.getBoolean("active")), companyId, userId)
            .stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ApiErrorCode.CUSTOMER_NOT_FOUND, "The customer does not belong to this company"));
    }

    @Transactional
    public CustomerResponse update(UUID companyId, UUID userId, CustomerProfileRequest request) {
        get(companyId, userId);
        jdbc.update("""
            INSERT INTO company_customer_profiles(company_id,user_id,phone,notes) VALUES (?,?,?,?)
            ON CONFLICT(company_id,user_id) DO UPDATE SET phone=excluded.phone,notes=excluded.notes,updated_at=now()
            """, companyId, userId, request.phone().trim(), request.notes().trim());
        audit.record(companyId, access.currentUserId(), "CUSTOMER_PROFILE_UPDATED", "USER", userId, null, null);
        return get(companyId, userId);
    }

    public BookingPageResponse history(UUID companyId, UUID userId, int page, int size) {
        get(companyId, userId);
        validatePage(page, size);
        var result = bookings.findByCompanyIdAndCustomerUserId(companyId, userId,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id"))));
        return new BookingPageResponse(presentation.present(result.getContent()), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                "Page must be non-negative and size must be between 1 and 50");
        }
    }
}
