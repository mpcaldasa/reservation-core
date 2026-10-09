package com.slotix.reservationcore.company;

import com.slotix.reservationcore.identity.MembershipResponse;

public record CompanyOnboardingResponse(CompanyResponse company, MembershipResponse administrator) {}
