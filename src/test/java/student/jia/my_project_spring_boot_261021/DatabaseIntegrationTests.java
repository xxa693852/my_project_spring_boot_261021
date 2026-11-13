package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import student.jia.my_project_spring_boot_261021.service.EventReminderDelivery;
import student.jia.my_project_spring_boot_261021.service.CancellationNotificationDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import student.jia.my_project_spring_boot_261021.constant.AuthOwnerTypes;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.repository.AuthTokenRepository;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.request.MemberPasswordUpdateRequest;
import student.jia.my_project_spring_boot_261021.request.MemberRegisterRequest;
import student.jia.my_project_spring_boot_261021.request.MemberUpdateRequest;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;
import student.jia.my_project_spring_boot_261021.response.TokenResponse;
import student.jia.my_project_spring_boot_261021.service.JwtService;
import student.jia.my_project_spring_boot_261021.service.MemberService;
import student.jia.my_project_spring_boot_261021.service.RegistrationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 使用真實 Spring 元件與 MySQL；每個測試結束後，Spring 會回滾測試交易。
// MockMvc 經過實際 Controller、Interceptor 與 ExceptionHandler，但不開放網路服務埠。
@SpringBootTest(properties = {"spring.jpa.show-sql=false", "app.cancellation-notifications.enabled=true"})
@Transactional
class DatabaseIntegrationTests {
    private static final String PASSWORD = "TestPassword123!";

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberService memberService;
    @Autowired private RegistrationService registrationService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EventRepository events;
    @Autowired private RegistrationRepository registrations;
    @Autowired private AuthTokenRepository tokens;

    private MockMvc mvc;
    private final List<Long> createdMemberIds = new ArrayList<>();
    private final List<Long> createdEventIds = new ArrayList<>();

    @AfterTransaction
    void verifyTestDataWasRolledBack() {
        // 在 Spring 回滾交易之後重新查詢，確認案例建立的資料沒有留在資料庫。
        for (Long memberId : createdMemberIds) {
            assertFalse(members.existsById(memberId), "測試會員應已回滾");
        }
        for (Long eventId : createdEventIds) {
            assertFalse(events.existsById(eventId), "測試活動應已回滾");
        }
    }

    @BeforeEach
    void prepare() {
        // 在建立案例資料前確認正在使用測試 schema，避免誤寫正式資料庫。
        String database = (String) entityManager.createNativeQuery("SELECT DATABASE()").getSingleResult();
        assertEquals("dnd_event_prod_test", database);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void cancellingAnEventQueuesOnlyCurrentRegistrations() throws Exception {
        Member admin = createMember();
        admin.setRole("ADMIN");
        members.saveAndFlush(admin);
        String authorization = bearer(login(admin));
        Member attending = createMember();
        Member previouslyCancelled = createMember();
        Event event = createEvent(admin.getId(), 3);
        registrationService.register(attending.getId(), event.getId());
        registrationService.register(previouslyCancelled.getId(), event.getId());
        registrationService.cancel(previouslyCancelled.getId(), event.getId());
        reloadFromDatabase();

        mvc.perform(delete("/admin/events/" + event.getId()).header("Authorization", authorization))
                .andExpect(status().isOk());
        reloadFromDatabase();
        assertEquals("CANCELLED", events.findById(event.getId()).orElseThrow().getStatus());
        assertEquals(0L, registrations.countByEventIdAndStatus(event.getId(), "REGISTERED"));
        mvc.perform(delete("/admin/events/" + event.getId()).header("Authorization", authorization))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/events/" + event.getId() + "/notifications")
                .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberId").value(attending.getId().intValue()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void eventPresentationSurvivesLegacyUpdatesAndAppearsOnPublicPages() throws Exception {
        Member admin = createMember();
        admin.setRole("ADMIN");
        members.saveAndFlush(admin);
        String authorization = bearer(login(admin));
        Event legacyEvent = createEvent(admin.getId(), 2);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String body = """
                {
                    "title": "Presentation Integration Event",
                    "description": "Description",
                    "hostName": "Host",
                    "location": "Taipei",
                    "startAt": "%s",
                    "endAt": "%s",
                    "registrationDeadline": "%s",
                    "capacity": 2,
                    "beginnerFriendly": true%s
                }
                """.formatted(now.plusDays(2), now.plusDays(3), now.plusDays(1), "%s");

        mvc.perform(get("/events/" + legacyEvent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverTheme").value("tavern"))
                .andExpect(jsonPath("$.tags.length()").value(0));

        String presentation = """
                , "coverTheme": "forest", "tags": [" 劇情向 ", "提供預製角色", "劇情向"]
                """;
        mvc.perform(post("/admin/events").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(presentation)))
                .andExpect(status().isCreated());
        reloadFromDatabase();
        Event created = events.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "id")).getFirst();
        createdEventIds.add(created.getId());

        mvc.perform(get("/events/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverTheme").value("forest"))
                .andExpect(jsonPath("$.tags[0]").value("劇情向"))
                .andExpect(jsonPath("$.tags[1]").value("提供預製角色"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.beginnerFriendly").value(true));
        mvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].coverTheme", hasItem("forest")))
                .andExpect(jsonPath("$[*].tags[*]", hasItem("提供預製角色")));

        mvc.perform(put("/admin/events/" + created.getId()).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted("")))
                .andExpect(status().isOk());
        reloadFromDatabase();
        mvc.perform(get("/admin/events/" + created.getId()).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverTheme").value("forest"))
                .andExpect(jsonPath("$.tags.length()").value(2));

        String invalid = ", \"coverTheme\": \"unknown\", \"tags\": [\"12345678901234567\"]";
        mvc.perform(put("/admin/events/" + created.getId()).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coverTheme").exists());
        String tooMany = ", \"tags\": [\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]";
        mvc.perform(put("/admin/events/" + created.getId()).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(tooMany)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tags").exists());

        String clear = ", \"coverTheme\": \"castle\", \"tags\": []";
        mvc.perform(put("/admin/events/" + created.getId()).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(clear)))
                .andExpect(status().isOk());
        reloadFromDatabase();
        mvc.perform(get("/events/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverTheme").value("castle"))
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void adminActivityPagesHaveStableOrderAndPreserveLegacyList() throws Exception {
        Member admin = createMember();
        admin.setRole("ADMIN");
        members.saveAndFlush(admin);
        TokenResponse adminLogin = login(admin);
        TokenResponse memberLogin = login(createMember());
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            Event event = createEvent(admin.getId(), 2);
            ids.add(event.getId());
        }
        reloadFromDatabase();

        mvc.perform(get("/admin/events/page").header("Authorization", bearer(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.events.length()").value(10))
                .andExpect(jsonPath("$.events[0].id").value(ids.get(11).intValue()))
                .andExpect(jsonPath("$.events[9].id").value(ids.get(2).intValue()));
        mvc.perform(get("/admin/events/page").param("page", "2")
                .header("Authorization", bearer(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.events[0].id").value(ids.get(1).intValue()))
                .andExpect(jsonPath("$.events[1].id").value(ids.get(0).intValue()));
        mvc.perform(get("/admin/events/page").param("page", "0")
                .header("Authorization", bearer(adminLogin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/events/page").header("Authorization", bearer(memberLogin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/events").header("Authorization", bearer(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void publicActivityPagesKeepStableOrderAndExcludeCancelledEvents() throws Exception {
        Member owner = createMember();
        List<Event> existingEvents = events.findByStatusOrderByStartAtAsc("OPEN");
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        if (!existingEvents.isEmpty()) {
            start = existingEvents.get(0).getStartAt().minusDays(1);
        }

        List<Long> openIds = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            Event event = createEvent(owner.getId(), 2);
            event.setStartAt(start);
            event.setEndAt(start.plusHours(4));
            event.setRegistrationDeadline(start.minusHours(1));
            if (index == 0) {
                event.setStatus("CANCELLED");
            } else {
                openIds.add(event.getId());
            }
        }
        reloadFromDatabase();

        mvc.perform(get("/events/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalEvents").value(existingEvents.size() + 7))
                .andExpect(jsonPath("$.events.length()").value(6))
                .andExpect(jsonPath("$.events[0].id").value(openIds.get(0).intValue()))
                .andExpect(jsonPath("$.events[5].id").value(openIds.get(5).intValue()));
        mvc.perform(get("/events/page").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].id").value(openIds.get(6).intValue()));
        mvc.perform(get("/events/page").param("page", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(existingEvents.size() + 7));
    }

    @Test
    void registrationCancellationAndReregistrationPersistThroughApi() throws Exception {
        Member member = createMember();
        TokenResponse login = login(member);
        Event event = createEvent(member.getId(), 2);

        mvc.perform(post("/registration").header("Authorization", bearer(login))
                .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());
        reloadFromDatabase();
        Optional<Registration> optionalOriginal =
                registrations.findByMemberIdAndEventId(member.getId(), event.getId());
        assertTrue(optionalOriginal.isPresent());
        Registration original = optionalOriginal.get();
        Long registrationId = original.getId();
        assertEquals("REGISTERED", original.getStatus());

        mvc.perform(delete("/registration/events/" + event.getId()).header("Authorization", bearer(login)))
                .andExpect(status().isOk());
        reloadFromDatabase();
        Optional<Registration> optionalCancelled = registrations.findById(registrationId);
        assertTrue(optionalCancelled.isPresent());
        Registration cancelled = optionalCancelled.get();
        assertEquals("CANCELLED", cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
        mvc.perform(get("/registration").header("Authorization", bearer(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].canCancel").value(false));

        mvc.perform(post("/registration").header("Authorization", bearer(login))
                .param("eventId", event.getId().toString()))
                .andExpect(status().isCreated());
        reloadFromDatabase();
        Optional<Registration> optionalRestored =
                registrations.findByMemberIdAndEventId(member.getId(), event.getId());
        assertTrue(optionalRestored.isPresent());
        Registration restored = optionalRestored.get();
        assertEquals(registrationId, restored.getId());
        assertEquals("REGISTERED", restored.getStatus());
        assertNull(restored.getCancelledAt());
        assertEquals(1L, registrations.countByEventIdAndStatus(event.getId(), "REGISTERED"));
    }

    @Test
    void duplicateRegistrationAndFullCapacityKeepOriginalErrors() throws Exception {
        Member first = createMember();
        Member second = createMember();
        TokenResponse firstLogin = login(first);
        TokenResponse secondLogin = login(second);
        Event event = createEvent(first.getId(), 1);
        registrationService.register(first.getId(), event.getId());
        reloadFromDatabase();

        mvc.perform(post("/registration").header("Authorization", bearer(firstLogin))
                .param("eventId", event.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ResponseMessages.getMessage(ResponseMessages.EVENT_REGISTERED_REPEAT)));
        mvc.perform(post("/registration").header("Authorization", bearer(secondLogin))
                .param("eventId", event.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ResponseMessages.getMessage(ResponseMessages.EVENT_FULL)));
        assertEquals(1L, registrations.countByEventIdAndStatus(event.getId(), "REGISTERED"));
        assertTrue(registrations.findByMemberIdAndEventId(second.getId(), event.getId()).isEmpty());
    }

    @Test
    void deadlinePreventsRegistrationAndCancellation() throws Exception {
        Member member = createMember();
        TokenResponse login = login(member);
        Event event = createEvent(member.getId(), 2);
        event.setRegistrationDeadline(LocalDateTime.now().minusMinutes(1));
        Registration registration = registrations.saveAndFlush(new Registration(member.getId(), event.getId()));
        reloadFromDatabase();

        mvc.perform(post("/registration").header("Authorization", bearer(login))
                .param("eventId", event.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ResponseMessages.getMessage(ResponseMessages.EVENT_REGISTRATION_DEADLINE_PASSED)));
        mvc.perform(delete("/registration/events/" + event.getId()).header("Authorization", bearer(login)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ResponseMessages.getMessage(ResponseMessages.REGISTRATION_CANCELLATION_CLOSED)));
        reloadFromDatabase();
        Optional<Registration> stored = registrations.findById(registration.getId());
        assertTrue(stored.isPresent());
        assertEquals("REGISTERED", stored.get().getStatus());
    }

    @Test
    void historyPreservesOrderAndIncludesCancelledEvents() {
        Member member = createMember();
        Event open = createEvent(member.getId(), 2);
        Event cancelled = createEvent(member.getId(), 2);
        cancelled.setStatus("CANCELLED");
        Registration older = registrations.saveAndFlush(new Registration(member.getId(), open.getId()));
        older.setRegisteredAt(LocalDateTime.now().minusHours(2));
        Registration newer = registrations.saveAndFlush(new Registration(member.getId(), cancelled.getId()));
        newer.setStatus("CANCELLED");
        newer.setCancelledAt(LocalDateTime.now());
        reloadFromDatabase();

        List<RegistrationResponse> history = registrationService.findMyRegistrations(member.getId());

        assertEquals(2, history.size());
        assertEquals(cancelled.getId(), history.get(0).getEventId());
        assertEquals(cancelled.getTitle(), history.get(0).getEventTitle());
        assertEquals("CANCELLED", history.get(0).getEventStatus());
        assertFalse(history.get(0).isCanCancel());
        assertEquals(open.getId(), history.get(1).getEventId());
        assertTrue(history.get(1).isCanCancel());
    }

    @Test
    void profilePasswordAndLogoutChangesReachDatabase() throws Exception {
        Member member = createMember();
        TokenResponse login = login(member);
        MemberUpdateRequest profile = new MemberUpdateRequest();
        profile.setName("Updated Member");
        profile.setEmail("updated-" + UUID.randomUUID() + "@example.com");
        memberService.updateProfile(member.getId(), profile);
        MemberPasswordUpdateRequest password = new MemberPasswordUpdateRequest();
        password.setOldPassword(PASSWORD);
        password.setPassword("ChangedPassword123!");
        password.setConfirmPassword("ChangedPassword123!");
        memberService.updatePassword(member.getId(), password);
        reloadFromDatabase();

        Optional<Member> optionalStored = members.findById(member.getId());
        assertTrue(optionalStored.isPresent());
        Member stored = optionalStored.get();
        assertEquals(profile.getName(), stored.getName());
        assertEquals(profile.getEmail(), stored.getEmail());
        assertTrue(passwordEncoder.matches("ChangedPassword123!", stored.getPassword()));
        assertFalse(passwordEncoder.matches(PASSWORD, stored.getPassword()));
        mvc.perform(get("/members/me").header("Authorization", bearer(login)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Member"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(post("/members/logout").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearer(login))
                .content("{\"refreshToken\":\"" + login.getRefreshToken() + "\"}"))
                .andExpect(status().isOk());
        reloadFromDatabase();
        assertTrue(tokens.findActiveByAccessTokenHashAndOwnerType(
                jwtService.hashToken(login.getAccessToken()), AuthOwnerTypes.MEMBER).isEmpty());
        mvc.perform(get("/members/me").header("Authorization", bearer(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingMemberPersistsRevocationOfAllTokens() {
        Member member = createMember();
        TokenResponse login = login(member);
        // 另外建立一筆登入紀錄，確認迴圈不只撤銷第一筆 Token。
        AuthToken additional = tokens.saveAndFlush(new AuthToken(AuthOwnerTypes.MEMBER, member.getId(),
                jwtService.hashToken("access-" + UUID.randomUUID()),
                jwtService.hashToken("refresh-" + UUID.randomUUID()),
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(1)));
        reloadFromDatabase();

        memberService.disableMember(member.getId());
        reloadFromDatabase();

        Optional<Member> storedMember = members.findById(member.getId());
        assertTrue(storedMember.isPresent());
        assertEquals("DISABLED", storedMember.get().getStatus());
        assertTrue(tokens.findByOwnerTypeAndOwnerIdAndRevoked(AuthOwnerTypes.MEMBER, member.getId(), (byte) 0).isEmpty());
        Optional<AuthToken> storedToken = tokens.findById(additional.getId());
        assertTrue(storedToken.isPresent());
        assertEquals((byte) 1, storedToken.get().getRevoked());
        assertNotNull(storedToken.get().getDeletedAt());
        assertTrue(tokens.findActiveByRefreshTokenHashAndOwnerType(
                jwtService.hashToken(login.getRefreshToken()), AuthOwnerTypes.MEMBER).isEmpty());
    }

    @Test
    void expiredAccessTokenRotatesTokensAndRevokesOldRecord() throws Exception {
        Member member = createMember();
        LocalDateTime accessExpiry = LocalDateTime.now().minusMinutes(5);
        LocalDateTime refreshExpiry = LocalDateTime.now().plusDays(1);
        String access = jwtService.generateAccessToken(AuthOwnerTypes.MEMBER, member.getId(), accessExpiry);
        String refresh = jwtService.generateRefreshToken(AuthOwnerTypes.MEMBER, member.getId(), refreshExpiry);
        AuthToken old = tokens.saveAndFlush(new AuthToken(AuthOwnerTypes.MEMBER, member.getId(),
                jwtService.hashToken(access), jwtService.hashToken(refresh), accessExpiry, refreshExpiry));
        reloadFromDatabase();

        MvcResult result = mvc.perform(get("/members/me").header("Authorization", "Bearer " + access)
                .header("X-Refresh-Token", refresh))
                .andExpect(status().isOk()).andExpect(header().exists("X-New-Access-Token"))
                .andExpect(header().exists("X-New-Refresh-Token")).andReturn();
        String newAccess = result.getResponse().getHeader("X-New-Access-Token");
        String newRefresh = result.getResponse().getHeader("X-New-Refresh-Token");
        reloadFromDatabase();
        Optional<AuthToken> storedOldToken = tokens.findById(old.getId());
        assertTrue(storedOldToken.isPresent());
        assertEquals((byte) 1, storedOldToken.get().getRevoked());
        assertNotNull(storedOldToken.get().getDeletedAt());
        assertNotEquals(access, newAccess);
        assertNotEquals(refresh, newRefresh);
        assertTrue(tokens.findActiveByAccessTokenHashAndOwnerType(
                jwtService.hashToken(newAccess), AuthOwnerTypes.MEMBER).isPresent());
        mvc.perform(get("/members/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
        mvc.perform(get("/members/me").header("Authorization", "Bearer " + access)
                .header("X-Refresh-Token", refresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationAndOwnershipChecksRejectInvalidRequests() throws Exception {
        Member member = createMember();
        Member other = createMember();
        TokenResponse login = login(member);
        mvc.perform(get("/members/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/members/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/members/" + other.getId()).header("Authorization", bearer(login)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/members/logout").header("Authorization", bearer(login))
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"missing\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.refreshToken").value(ResponseMessages.getMessage(ResponseMessages.TOKEN_INVALID)));
    }

    @Test
    void validationAndMissingResourcesUseOriginalHttpResponses() throws Exception {
        mvc.perform(post("/members").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.account").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(get("/events/" + Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ResponseMessages.getMessage(ResponseMessages.EVENT_NOT_FOUND)));
        mvc.perform(post("/members/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"missing-" + UUID.randomUUID() + "\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.account").value(ResponseMessages.getMessage(ResponseMessages.MEMBER_LOGIN_FAILED)));
    }

    private Member createMember() {
        // 每個案例使用獨立帳號，不依賴既有 seed 或固定 ID。
        String suffix = UUID.randomUUID().toString().replace("-", "");
        MemberRegisterRequest request = new MemberRegisterRequest();
        request.setAccount("it_" + suffix);
        request.setName("Integration Member");
        request.setEmail("it_" + suffix + "@example.com");
        request.setPassword(PASSWORD);
        request.setConfirmPassword(PASSWORD);
        memberService.register(request);
        Optional<Member> member = members.findByAccountAndStatus(request.getAccount(), "ACTIVE");
        assertTrue(member.isPresent());
        createdMemberIds.add(member.get().getId());
        return member.get();
    }

    private TokenResponse login(Member member) {
        MemberLoginRequest request = new MemberLoginRequest();
        request.setAccount(member.getAccount());
        request.setPassword(PASSWORD);
        return memberService.login(request);
    }

    private Event createEvent(Long ownerId, int capacity) {
        LocalDateTime now = LocalDateTime.now();
        Event event = new Event("Integration Event " + UUID.randomUUID(), "Description", "Host", "Taipei",
                now.plusDays(2), now.plusDays(3), now.plusDays(1), capacity, true, ownerId);
        Event saved = events.saveAndFlush(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private String bearer(TokenResponse login) {
        return "Bearer " + login.getAccessToken();
    }

    @Test
    void registrationRejectsOverlappingEventsAtTheSameVenue() {
        Member member = createMember();
        Event first = createEvent(member.getId(), 2);
        Event adjacent = createEvent(member.getId(), 2);
        Event overlapping = createEvent(member.getId(), 2);
        adjacent.setStartAt(first.getEndAt());
        adjacent.setEndAt(first.getEndAt().plusHours(2));
        overlapping.setStartAt(first.getEndAt().minusMinutes(30));
        overlapping.setEndAt(first.getEndAt().plusHours(1));
        reloadFromDatabase();

        registrationService.register(member.getId(), first.getId());
        registrationService.register(member.getId(), adjacent.getId());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registrationService.register(member.getId(), overlapping.getId()));
        assertEquals("你已報名另一場時段重疊的活動", error.getMessage());
        assertEquals(0, registrations.countByEventIdAndStatus(overlapping.getId(), "REGISTERED"));
    }


    @Test
    void remindersUsePrimaryAndLateWindowsAndSendOnlyOnce() {
        Member owner = createMember();
        Member early = createMember();
        Member late = createMember();
        Member tooLate = createMember();
        Member cancelled = createMember();
        Event event = createEvent(owner.getId(), 5);
        LocalDateTime start = LocalDateTime.of(2099, 1, 10, 14, 0);
        event.setStartAt(start);
        event.setEndAt(start.plusHours(4));
        event.setRegistrationDeadline(start.minusHours(2));
        event.setLocation("Taipei & Table 2");
        LocalDateTime primary = start.minusHours(24);
        LocalDateTime fallback = start.minusHours(4);
        saveReminderRegistration(early, event, primary.minusMinutes(1), false);
        saveReminderRegistration(late, event, primary.plusMinutes(1), false);
        saveReminderRegistration(tooLate, event, fallback, false);
        saveReminderRegistration(cancelled, event, primary.minusMinutes(1), true);
        reloadFromDatabase();

        var sender = org.mockito.Mockito.mock(CancellationNotificationDelivery.class,
                invocation -> { throw new IllegalStateException("Simulated ambiguous delivery"); });
        var delivery = new EventReminderDelivery(jdbc, members, sender, false, "https://example.com/");
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(delivery, "deliverDue", primary);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(delivery, "deliverDue", fallback);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(delivery, "deliverDue", fallback.plusMinutes(1));

        var invocations = org.mockito.Mockito.mockingDetails(sender).getInvocations();
        assertEquals(2, invocations.size());
        var calls = new ArrayList<>(invocations);
        assertEquals(early.getId(), ((Member) calls.get(0).getArgument(0)).getId());
        assertEquals(late.getId(), ((Member) calls.get(1).getArgument(0)).getId());
        String message = calls.get(1).getArgument(2);
        assertTrue(message.contains("Taipei+%26+Table+2"));
        assertTrue(message.contains("https://example.com/my-registrations.html"));
    }

    private void saveReminderRegistration(Member member, Event event, LocalDateTime time, boolean cancelled) {
        Registration registration = registrations.saveAndFlush(new Registration(member.getId(), event.getId()));
        registration.setRegisteredAt(time);
        if (cancelled) {
            registration.setStatus("CANCELLED");
            registration.setCancelledAt(time.plusMinutes(1));
        }
        registrations.save(registration);
    }


    @Test
    void adminSuspensionBlocksNewRegistrationWithoutBlockingCancellation() throws Exception {
        Member admin = createMember();
        admin.setRole("ADMIN");
        members.saveAndFlush(admin);
        Member player = createMember();
        String adminAuth = bearer(login(admin));
        String playerAuth = bearer(login(player));
        Event existing = createEvent(admin.getId(), 2);
        Event another = createEvent(admin.getId(), 2);
        registrationService.register(player.getId(), existing.getId());
        String path = "/admin/members/" + player.getId();
        String suspend = "{\"action\":\"SUSPEND\",\"reason\":\"測試\"}";

        mvc.perform(get("/admin/members").header("Authorization", playerAuth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/members").param("query", player.getEmail()).header("Authorization", adminAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(player.getId()));
        mvc.perform(post(path + "/suspension").header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content(suspend))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", adminAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.member.suspended").value(true))
                .andExpect(jsonPath("$.member.password").doesNotExist())
                .andExpect(jsonPath("$.actions[0].reason").value("測試"))
                .andExpect(jsonPath("$.registrations[0].status").value("REGISTERED"));
        mvc.perform(post("/registration").param("eventId", another.getId().toString())
                .header("Authorization", playerAuth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("帳號停權中，無法報名新活動"));
        mvc.perform(delete("/registration/events/" + existing.getId()).header("Authorization", playerAuth))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/suspension").header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"LIFT\",\"reason\":\"解除測試\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", adminAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.member.suspended").value(false));
        mvc.perform(post("/registration").param("eventId", another.getId().toString())
                .header("Authorization", playerAuth)).andExpect(status().isCreated());
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        player.setSuspendedAt(now.minusDays(1));
        player.setSuspendedUntil(now);
        assertFalse(player.isSuspendedAt(now));
        assertTrue(player.isSuspendedAt(now.minusSeconds(1)));
    }

    private void reloadFromDatabase() {
        // flush 將變更送到 MySQL；clear 清除 JPA 快取，後續查詢才會重新讀取資料庫。
        // 這不會提交交易，案例結束後仍會回滾。
        entityManager.flush();
        entityManager.clear();
    }
}
