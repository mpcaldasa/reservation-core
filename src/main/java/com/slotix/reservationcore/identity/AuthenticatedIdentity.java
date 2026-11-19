package com.slotix.reservationcore.identity;

import com.slotix.reservationcore.common.AuthenticatedPrincipal;

import java.util.List;

public record AuthenticatedIdentity(AuthenticatedPrincipal principal, List<String> roles) {}
