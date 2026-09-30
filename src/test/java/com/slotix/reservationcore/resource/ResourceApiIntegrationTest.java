package com.slotix.reservationcore.resource;

import com.slotix.reservationcore.PostgresIntegrationTestSupport;
import com.slotix.reservationcore.common.JwtService;
import com.slotix.reservationcore.company.Company;
import com.slotix.reservationcore.company.CompanyRepository;
import com.slotix.reservationcore.identity.CompanyMembership;
import com.slotix.reservationcore.identity.CompanyMembershipRepository;
import com.slotix.reservationcore.identity.MembershipRole;
import com.slotix.reservationcore.identity.User;
import com.slotix.reservationcore.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyMembershipRepository companyMembershipRepository;
    @Autowired private ResourceRepository resourceRepository;

    @BeforeEach
    void cleanDatabase() {
        resourceRepository.deleteAll();
        companyMembershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void companyAdminCanCreateAndListResourcesInTheirOwnCompany() throws Exception {
        Company company = activeCompany("main-office");
        String token = companyAdminToken(company);

        mockMvc.perform(post("/api/v1/companies/{companyId}/resources", company.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Boardroom A","description":"Seats up to twelve people","resourceType":"SPACE","capacity":12,"visibility":"MEMBERS"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.companyId").value(company.getId().toString()))
            .andExpect(jsonPath("$.name").value("Boardroom A"))
            .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/companies/{companyId}/resources", company.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Boardroom A"));
    }

    @Test
    void companyAdminCannotReadResourcesFromAnotherCompany() throws Exception {
        Company authorizedCompany = activeCompany("authorized-company");
        Company otherCompany = activeCompany("other-company");
        String token = companyAdminToken(authorizedCompany);

        mockMvc.perform(get("/api/v1/companies/{companyId}/resources", otherCompany.getId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    private Company activeCompany(String slug) {
        Company company = Company.create(slug + " Legal Name", slug + " Display Name", slug + "-" + UUID.randomUUID(), slug + "@example.test");
        company.activate();
        return companyRepository.save(company);
    }

    private String companyAdminToken(Company company) {
        User user = userRepository.save(User.create(UUID.randomUUID() + "@example.test", "unused-password-hash", "Company Administrator"));
        companyMembershipRepository.save(CompanyMembership.create(company.getId(), user.getId(), Set.of(MembershipRole.COMPANY_ADMIN)));
        return jwtService.generateToken(user.getId(), company.getId(), List.of("COMPANY_ADMIN"));
    }
}
