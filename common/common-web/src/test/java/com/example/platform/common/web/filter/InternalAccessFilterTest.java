package com.example.platform.common.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.platform.common.core.constant.Headers;
import com.example.platform.common.security.InternalAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class InternalAccessFilterTest {

    private static final String SECRET = "test-internal-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InternalAccessFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setSecret(SECRET);
        filter = new InternalAccessFilter(properties, objectMapper);
    }

    @Test
    void rejectsBusinessRequestWithoutInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/connections");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).as("不该继续走到业务处理").isNull();
    }

    @Test
    void rejectsBusinessRequestWithWrongInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/connections");
        request.addHeader(Headers.INTERNAL_TOKEN, "forged");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsBusinessRequestFromGateway() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/connections");
        request.addHeader(Headers.INTERNAL_TOKEN, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void letsPermittedAuthEndpointsThroughWithoutToken() throws Exception {
        for (String path : new String[]{"/api/auth/login", "/api/auth/refresh", "/api/auth/logout"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as(path + " 应放行").isNotNull();
        }
    }

    @Test
    void doesNotTouchNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsEverythingWhenSecretIsMissing() throws Exception {
        InternalAuthProperties blank = new InternalAuthProperties();
        blank.setSecret("");
        InternalAccessFilter strictFilter = new InternalAccessFilter(blank, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/db-types");
        request.addHeader(Headers.INTERNAL_TOKEN, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        strictFilter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
