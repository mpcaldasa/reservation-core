package com.slotix.reservationcore.company;

import com.slotix.reservationcore.identity.InitialAdministratorRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CompanyOnboardingRequest(@NotNull @Valid CreateCompanyRequest company,
                                       @NotNull @Valid InitialAdministratorRequest administrator) {}
