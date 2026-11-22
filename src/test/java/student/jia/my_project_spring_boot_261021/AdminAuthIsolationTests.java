package student.jia.my_project_spring_boot_261021;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import student.jia.my_project_spring_boot_261021.config.WebConfig;
import student.jia.my_project_spring_boot_261021.controller.AdminAuthController;
import student.jia.my_project_spring_boot_261021.controller.MemberController;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.interceptor.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真正 WebConfig 與攔截器驗證隔離；Repository 模擬，不讀寫實際資料庫。 */
class AdminAuthIsolationTests {
    private static final Instant START = Instant.parse("2026-11-20T00:00:00Z");
    private MockMvc mvc;
    private AnnotationConfigWebApplicationContext context;
    private MemberRepository members;
    private Clock clock;
    private Member admin;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        members = context.getBean(MemberRepository.class);
        clock = context.getBean(Clock.class);
        when(clock.instant()).thenReturn(START);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        admin = new Member("admin", "hash", "管理員", "admin@example.com");
        admin.setId(7L);
        admin.setRole("ADMIN");
        when(members.findByAccountAndStatus("admin", "ACTIVE")).thenReturn(Optional.of(admin));
        when(members.findByIdAndStatus(7L, "ACTIVE")).thenReturn(Optional.of(admin));
        when(context.getBean(PasswordEncoder.class).matches("password", "hash")).thenReturn(true);
    }

    @org.junit.jupiter.api.AfterEach
    void closeContext() { context.close(); }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/admin/auth/login").header("X-Admin-Login", "1")
                .contentType("application/json").content("{\"account\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.role").value("ADMIN")).andReturn();
    }

    private MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String csrf(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.csrfToken");
    }

    @Test
    void memberJwtCannotReadOrWriteAnyAdminEndpointOrEnterPages() throws Exception {
        JwtService jwt = context.getBean(JwtService.class);
        String token = jwt.generateAccessToken("MEMBER", 7L);
        for (String path : new String[] {"/admin/probe", "/admin/auth/me", "/admin/events/1/payments", "/admin/members"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin-events.html").header("Authorization", "Bearer " + token))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin-login.html?returnTo=/admin-events.html"));
    }

    @Test
    void ordinaryDisabledSuspendedAndWrongPasswordCannotCreateAdminSession() throws Exception {
        for (String role : new String[] {"MEMBER", "ADMIN"}) {
            admin.setRole(role);
            when(context.getBean(PasswordEncoder.class).matches("password", "hash")).thenReturn(false);
            mvc.perform(post("/admin/auth/login").header("X-Admin-Login", "1")
                    .contentType("application/json").content("{\"account\":\"admin\",\"password\":\"password\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(result -> assertNull(result.getRequest().getSession(false)));
        }
        admin.setSuspendedAt(java.time.LocalDateTime.ofInstant(START.minusSeconds(1), java.time.ZoneId.of("Asia/Taipei")));
        when(context.getBean(PasswordEncoder.class).matches("password", "hash")).thenReturn(true);
        mvc.perform(post("/admin/auth/login").header("X-Admin-Login", "1")
                .contentType("application/json").content("{\"account\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
        when(members.findByAccountAndStatus("admin", "ACTIVE")).thenReturn(Optional.empty());
        mvc.perform(post("/admin/auth/login").header("X-Admin-Login", "1")
                .contentType("application/json").content("{\"account\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginNeedsCustomHeaderAndWritesNeedCorrectCsrf() throws Exception {
        mvc.perform(post("/admin/auth/login").contentType("application/json")
                .content("{\"account\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        mvc.perform(post("/admin/probe").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/probe").session(session).header("X-Admin-CSRF", "wrong"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/probe").session(session).header("X-Admin-CSRF", csrf(signedIn)))
                .andExpect(status().isOk()).andExpect(content().string("7"));
    }

    @Test
    void backgroundReadsDoNotExtendIdleAndExactly30MinutesExpires() throws Exception {
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        when(clock.instant()).thenReturn(START.plusSeconds(1799));
        mvc.perform(get("/admin/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.idleExpiresAt").value(START.plusSeconds(1800).toEpochMilli()));
        when(clock.instant()).thenReturn(START.plusSeconds(1800));
        mvc.perform(post("/admin/auth/activity").session(session).header("X-Admin-CSRF", csrf(signedIn)))
                .andExpect(status().isUnauthorized());
        when(clock.instant()).thenReturn(START.plusSeconds(1801));
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void activityExtendsOnlyLiveSessionAndLogoutRevokesIt() throws Exception {
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        when(clock.instant()).thenReturn(START.plusSeconds(1799));
        mvc.perform(post("/admin/auth/activity").session(session).header("X-Admin-CSRF", csrf(signedIn)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idleExpiresAt").value(START.plusSeconds(3599).toEpochMilli()));
        when(clock.instant()).thenReturn(START.plusSeconds(1800));
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/auth/logout").session(session).header("X-Admin-CSRF", csrf(signedIn)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void roleRevocationAndDisabledAccountTakeEffectOnNextRequest() throws Exception {
        MockHttpSession session = session(login());
        admin.setRole("MEMBER");
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
        admin.setRole("ADMIN");
        session = session(login());
        when(members.findByIdAndStatus(7L, "ACTIVE")).thenReturn(Optional.empty());
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminSessionCannotReplaceMemberJwtAndLoginRotatesSessionId() throws Exception {
        MockHttpSession original = new MockHttpSession();
        String oldId = original.getId();
        MvcResult login = mvc.perform(post("/admin/auth/login").session(original).header("X-Admin-Login", "1")
                .contentType("application/json").content("{\"account\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn();
        assertNotEquals(oldId, session(login).getId());
        assertEquals(1800, session(login).getMaxInactiveInterval());
        mvc.perform(get("/members/me").session(session(login))).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentActivityCannotRestoreAdminSessionAfterLogout() throws Exception {
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);
        request.addHeader("X-Admin-CSRF", csrf(signedIn));
        AdminAuthService auth = context.getBean(AdminAuthService.class);
        var reading = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var logoutStarted = new java.util.concurrent.CountDownLatch(1);
        var logoutFinished = new java.util.concurrent.CountDownLatch(1);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        when(members.findByIdAndStatus(7L, "ACTIVE")).thenAnswer(invocation -> {
            reading.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return Optional.of(admin);
        });
        try {
            var activity = workers.submit(() -> auth.require(request, true));
            assertTrue(reading.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var logout = workers.submit(() -> {
                logoutStarted.countDown();
                auth.logout(request);
                logoutFinished.countDown();
            });
            assertTrue(logoutStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            // 登出須等正在續期的交易離開相同的 Session 鎖，再撤銷狀態。
            assertFalse(logoutFinished.await(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown();
            activity.get(5, java.util.concurrent.TimeUnit.SECONDS);
            logout.get(5, java.util.concurrent.TimeUnit.SECONDS);
            mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void adminMemberReadSharesSameIdleDeadlineAndCannotOutliveAdminSession() throws Exception {
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        when(context.getBean(MemberService.class).findOneById(7L)).thenReturn(admin);
        when(clock.instant()).thenReturn(START.plusSeconds(1799));
        mvc.perform(get("/members/me").session(session).header("X-Admin-Front", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        when(clock.instant()).thenReturn(START.plusSeconds(1800));
        mvc.perform(get("/members/me").session(session).header("X-Admin-Front", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/probe").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void rosterRequiresAdminSessionAndCancellationRequiresCsrf() throws Exception {
        mvc.perform(get("/admin/events/11/registrations")).andExpect(status().isUnauthorized());
        MvcResult signedIn = login();
        MockHttpSession session = session(signedIn);
        mvc.perform(get("/admin/events/11/registrations").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/events/11/registrations/20/cancel").session(session)
                .contentType("application/json").content("{\"reason\":\"事故\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/events/11/registrations/20/cancel").session(session)
                .header("X-Admin-CSRF", csrf(signedIn))
                .contentType("application/json").content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/admin/events/11/registrations/20/cancel").session(session)
                .header("X-Admin-CSRF", csrf(signedIn))
                .contentType("application/json").content("{\"reason\":\"事故\"}"))
                .andExpect(status().isOk());
        verify(context.getBean(AdminRegistrationService.class)).cancel(7L, 11L, 20L, "事故");
    }

    @RestController
    static class Probe {
        @RequestMapping({"/admin/probe", "/admin/members", "/admin/events/1/payments", "/admin-events.html"})
        public String admin(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long id) { return id.toString(); }
    }

    @Configuration
    @EnableWebMvc
    @Import({student.jia.my_project_spring_boot_261021.controller.AdminRegistrationController.class, WebConfig.class, AdminAuthController.class, MemberController.class, Probe.class,
        student.jia.my_project_spring_boot_261021.exception.GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean AdminRegistrationService roster() { return mock(AdminRegistrationService.class); }
        @Bean MemberRepository members() { return mock(MemberRepository.class); }
        @Bean PasswordEncoder passwords() { return mock(PasswordEncoder.class); }
        @Bean Clock clock() { return mock(Clock.class); }
        @Bean AdminAuthService adminAuth(MemberRepository members, PasswordEncoder passwords, Clock clock) {
            return new AdminAuthService(members, passwords, clock);
        }
        @Bean AdminAuthInterceptor adminInterceptor(AdminAuthService auth) { return new AdminAuthInterceptor(auth); }
        @Bean JwtService jwt() { return new JwtService("admin-isolation-test-secret-at-least-32-bytes", 60, 604800); }
        @Bean AuthTokenRepository tokens() { return mock(AuthTokenRepository.class); }
        @Bean AuthInterceptor memberInterceptor(JwtService jwt, AuthTokenRepository tokens, AdminAuthService adminAuth) { return new AuthInterceptor(jwt, tokens, adminAuth); }
        @Bean MemberService memberService() { return mock(MemberService.class); }
    }
}
