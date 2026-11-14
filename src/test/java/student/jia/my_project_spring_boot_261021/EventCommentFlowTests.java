package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@Transactional
class EventCommentFlowTests {

    private static final String PASSWORD = "CommentTester123!";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    @Autowired private EventRepository events;
    @Autowired private MemberRepository members;
    @Autowired private PasswordEncoder encoder;

    private MockMvc mvc;

    @BeforeEach
    void prepare() {
        assertEquals("dnd_event_prod_test", entityManager.createNativeQuery("SELECT DATABASE()").getSingleResult());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void visitorReadsAndUnregisteredMemberCanPostWhileAdminCanHideAndRestore() throws Exception {
        Member author = member("MEMBER");
        String authorToken = login(author);
        String otherToken = login(member("MEMBER"));
        String adminToken = login(member("ADMIN"));
        Event event = event(author);
        String details = "/events/" + event.getId();
        String publicPath = details + "/comments";
        String memberPath = "/members/me" + publicPath;
        String body = "<b>要自備骰子嗎？</b>";

        mvc.perform(post(memberPath).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"unauthenticated\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post(memberPath).header("Authorization", authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + "字".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(memberPath).header("Authorization", authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}")).andExpect(status().isCreated());
        mvc.perform(get(publicPath)).andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].body").value(body))
                .andExpect(jsonPath("$.comments[0].canDelete").value(false))
                .andExpect(jsonPath("$.canPost").value(false));
        int commentId = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(get(memberPath).header("Authorization", authorToken)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.comments[0].canDelete").value(true))
                        .andReturn().getResponse().getContentAsString(), "$.comments[0].id");
        String comment = memberPath + "/" + commentId;
        String moderation = "/admin" + publicPath + "/" + commentId + "/visibility";
        String reason = "{\"reason\":\"測試隱藏\"}";

        mvc.perform(delete(comment).header("Authorization", otherToken)).andExpect(status().isNotFound());
        mvc.perform(put(moderation).header("Authorization", otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isBadRequest());
        mvc.perform(put(moderation).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isNoContent());
        mvc.perform(get(publicPath)).andExpect(jsonPath("$.comments").isEmpty());
        mvc.perform(get(memberPath).header("Authorization", authorToken))
                .andExpect(jsonPath("$.comments").isEmpty());
        mvc.perform(get(memberPath).header("Authorization", adminToken))
                .andExpect(jsonPath("$.comments[0].hiddenReason").value("測試隱藏"));
        mvc.perform(put(moderation).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(reason)).andExpect(status().isNoContent());
        mvc.perform(delete(moderation).header("Authorization", adminToken)).andExpect(status().isNoContent());
        mvc.perform(delete(moderation).header("Authorization", adminToken)).andExpect(status().isNoContent());

        var notices = entityManager.createNativeQuery("""
                SELECT member_id, event_title, reason, status FROM comment_moderation_notifications
                WHERE comment_id = :commentId ORDER BY id
                """).setParameter("commentId", commentId).getResultList();
        assertEquals(2, notices.size());
        Object[] hiddenNotice = (Object[]) notices.get(0);
        assertEquals(author.getId(), ((Number) hiddenNotice[0]).longValue());
        assertEquals(event.getTitle(), hiddenNotice[1]);
        assertEquals("測試隱藏", hiddenNotice[2]);
        assertEquals("PENDING", hiddenNotice[3]);
        Object[] restoredNotice = (Object[]) notices.get(1);
        assertEquals(author.getId(), ((Number) restoredNotice[0]).longValue());
        assertEquals(null, restoredNotice[2]);
        assertEquals("PENDING", restoredNotice[3]);
        mvc.perform(get(publicPath)).andExpect(jsonPath("$.comments[0].body").value(body));

        event.setStatus("CANCELLED");
        mvc.perform(get(details)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get(publicPath)).andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.comments[0].body").value(body));
        mvc.perform(post(memberPath).header("Authorization", authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"closed\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete(comment).header("Authorization", authorToken)).andExpect(status().isBadRequest());

        event.setStatus("OPEN");
        LocalDateTime previousStart = event.getStartAt();
        LocalDateTime previousEnd = event.getEndAt();
        LocalDateTime previousDeadline = event.getRegistrationDeadline();
        LocalDateTime past = LocalDateTime.now(TAIPEI).minusHours(3);
        event.setRegistrationDeadline(past.minusHours(1));
        event.setStartAt(past);
        event.setEndAt(past.plusHours(1));
        mvc.perform(get(publicPath)).andExpect(jsonPath("$.open").value(false));
        mvc.perform(post(memberPath).header("Authorization", authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"ended\"}")).andExpect(status().isBadRequest());

        event.setStartAt(previousStart);
        event.setEndAt(previousEnd);
        event.setRegistrationDeadline(previousDeadline);
        author.setSuspendedAt(LocalDateTime.now(TAIPEI).minusMinutes(1));
        mvc.perform(post(memberPath).header("Authorization", authorToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"suspended\"}")).andExpect(status().isBadRequest());
        author.setSuspendedUntil(LocalDateTime.now(TAIPEI).minusSeconds(1));
        mvc.perform(delete(comment).header("Authorization", authorToken)).andExpect(status().isNoContent());
        mvc.perform(get(publicPath)).andExpect(jsonPath("$.comments").isEmpty());
    }

    private Event event(Member creator) {
        LocalDateTime start = LocalDateTime.now(TAIPEI).plusDays(2);
        return events.saveAndFlush(new Event("留言測試", "介紹", "主持人", "台北",
                start, start.plusHours(3), start.minusHours(1), 6, true, creator.getId()));
    }

    private Member member(String role) {
        String account = "comment_" + UUID.randomUUID().toString().substring(0, 12);
        Member member = new Member(account, encoder.encode(PASSWORD), "留言測試會員", account + "@example.com");
        member.setRole(role);
        return members.saveAndFlush(member);
    }

    private String login(Member member) throws Exception {
        String response = mvc.perform(post("/members/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + member.getAccount() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
    }
}
