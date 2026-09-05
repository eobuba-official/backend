package com.piggyback.backend.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class JwtAuthFilterTest {

    private JwtProvider jwtProvider;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-for-jwt-filter-must-be-long-enough!");
        jwtProvider = new JwtProvider(properties);
        filter = new JwtAuthFilter(jwtProvider, new ObjectMapper());
    }

    @Test
    void 토큰_없이_보호_API_접근_시_401_UNAUTHORIZED_응답이다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, Mockito.mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"UNAUTHORIZED\"").contains("\"success\":false");
    }

    @Test
    void 유효한_토큰이면_userId_속성을_설정하고_통과시킨다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader("Authorization", "Bearer " + jwtProvider.createAccessToken(7L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(JwtAuthFilter.USER_ID_ATTRIBUTE)).isEqualTo(7L);
        Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void auth_경로는_필터를_거치지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/sms/request");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void Swagger_경로는_필터를_거치지_않는다() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/v3/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"))).isTrue();
    }
}
