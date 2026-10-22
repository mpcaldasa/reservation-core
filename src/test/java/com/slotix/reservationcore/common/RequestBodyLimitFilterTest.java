package com.slotix.reservationcore.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RequestBodyLimitFilterTest {
    @Test
    void rejectsOversizedBodiesWithoutTrustingContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/users/login");
        request.setContent(new byte[32769]);
        request.addHeader("Transfer-Encoding", "chunked");
        var response = new MockHttpServletResponse();
        var invoked = new AtomicBoolean();
        new RequestBodyLimitFilter().doFilter(request, response, (req, res) -> invoked.set(true));
        assertEquals(413, response.getStatus());
        assertFalse(invoked.get());
    }

    @Test
    void preservesAllowedBodyForDownstreamParsing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/users/login");
        request.setContent("{\"email\":\"user@example.test\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        new RequestBodyLimitFilter().doFilter(request, response, (req, res) ->
            assertArrayEquals(request.getContentAsByteArray(), req.getInputStream().readAllBytes()));
        assertEquals(200, response.getStatus());
    }
}
