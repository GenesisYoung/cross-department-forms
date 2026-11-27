package com.factory.collab.i18n;

import com.factory.collab.config.*;
import com.factory.collab.dto.Dtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class LanguageHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                Map.of("collab.cors.allowed-origins", "http://localhost:5173")));
        context.register(Config.class);
        context.refresh();
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new LanguageConfig.LanguageFilter(), context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, JwtAuthFilter.class, LanguageConfig.class, GlobalExceptionHandler.class, LocalizedErrorController.class, Endpoints.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean TokenAuthenticator authenticator() {
            TokenAuthenticator auth = mock(TokenAuthenticator.class);
            when(auth.authenticate(any())).thenReturn(Optional.of(new UsernamePasswordAuthenticationToken("member", null,
                    List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))));
            return auth;
        }
    }
    @RestController static class Endpoints {
        @PostMapping("/api/auth/login") public void login(@Valid @RequestBody LoginRequest request) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "error.invalidCredentials");
        }
        @PostMapping("/api/password") public void password(@Valid @RequestBody ChangePasswordRequest request) {}
        @GetMapping("/api/example") public void example(@RequestParam int count) { throw ApiException.badRequest("error.cellLimit", "A1"); }
    }
    @ParameterizedTest @CsvSource({"en,Invalid username or password", "zh-CN,用户名或密码错误", "zh-TW,使用者名稱或密碼錯誤"})
    void loginFollowsRequestLanguage(String language, String expected) throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", language).contentType("application/json")
                .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(expected))
                .andExpect(jsonPath("$.code").value("error.invalidCredentials")).andExpect(header().string("Content-Language", language));
    }
    @Test void coversSecurityAndRestoresLocaleBetweenRequests() throws Exception {
        mvc.perform(get("/api/example").header("Accept-Language", "zh-CN")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer member").header("Accept-Language", "zh-TW"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("沒有存取權限"));
        mvc.perform(get("/api/example")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Not authenticated"));
    }
    @Test void localizesContainerFallbackAndMissingRoutes() throws Exception {
        mvc.perform(get("/error").requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 404).header("Accept-Language", "zh-TW"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("找不到請求的資源"));
        mvc.perform(get("/api/nonexistent").header("Authorization", "Bearer member").header("Accept-Language", "zh-CN"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("找不到请求的资源"));
    }
    @Test void translatesConstraintDetailsAndNeverLeaksParserMessages() throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", "zh-CN").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能为空")))
                .andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/api/password").header("Authorization", "Bearer member").header("Accept-Language", "zh-TW")
                .contentType("application/json").content("{\"currentPassword\":\"a\",\"newPassword\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("6")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("長度")));
        mvc.perform(post("/api/auth/login").header("Accept-Language", "zh-TW").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("請求內容無效"));
        mvc.perform(get("/api/example").header("Authorization", "Bearer member").header("Accept-Language", "zh-CN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请求内容无效"));
        mvc.perform(get("/api/example?count=1").header("Authorization", "Bearer member").header("Accept-Language", "zh-TW"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("儲存格 A1 超出表單限制"));
    }
}
