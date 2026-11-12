package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.service.JwtService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 真實 Controller、Service、MySQL；僅模擬郵件傳輸，擷取實際 MailService 組成的信。
// 每個測試均回滾；另外以獨立本機測試服務驗證真正寄信及收信。
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@Transactional
class PasswordResetFlowTests {
    @Autowired private WebApplicationContext context;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberRepository members;
    @Autowired private PasswordResetTokenRepository resetTokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @MockitoBean private JavaMailSender sender;
    private MockMvc mvc;
    private String account;
    private String email;
    private static final String OLD = "OriginalPassword123!";
    private static final String NEW = "UpdatedPassword456!";

    @BeforeEach
    void prepare() {
        assertEquals("dnd_event_prod_test", entityManager.createNativeQuery("SELECT DATABASE()").getSingleResult());
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        account = "reset_" + UUID.randomUUID().toString().substring(0, 12);
        email = account + "@example.com";
    }

    private Member member() {
        return members.saveAndFlush(new Member(account, encoder.encode(OLD), "Reset tester", email));
    }

    private String requestMail() throws Exception {
        mvc.perform(post("/members/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage mail = captor.getValue();
        assertArrayEquals(new String[] {email}, mail.getTo());
        var matcher = Pattern.compile("/reset-password\\.html\\?token=([A-Za-z0-9_-]+)").matcher(mail.getText());
        assertTrue(matcher.find(), "信件須包含本專案的重設頁面連結");
        return matcher.group(1);
    }

    private String resetBody(String token) {
        return "{\"token\":\"" + token + "\",\"password\":\"" + NEW + "\",\"confirmPassword\":\"" + NEW + "\"}";
    }

    @Test
    void mailToNewPasswordLoginAndOneTimeUse() throws Exception {
        Member member = member();
        String token = requestMail();
        mvc.perform(get("/reset-password.html")).andExpect(status().isOk());
        mvc.perform(post("/members/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(token)))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Member updated = members.findById(member.getId()).get();
        assertTrue(encoder.matches(NEW, updated.getPassword()));
        assertFalse(encoder.matches(OLD, updated.getPassword()));
        mvc.perform(post("/members/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"" + NEW + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post("/members/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"" + account + "\",\"password\":\"" + OLD + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/members/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token").exists());
    }

    @Test
    void expiredLinkCannotChangePassword() throws Exception {
        Member member = member();
        String token = "expired-test-token";
        resetTokens.saveAndFlush(new PasswordResetToken(member.getId(), jwt.hashToken(token), LocalDateTime.now().minusMinutes(1)));
        mvc.perform(post("/members/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token").exists());
        assertTrue(encoder.matches(OLD, member.getPassword()));
    }

    @Test
    void unknownEmailUsesSameResponseAndSendsNothing() throws Exception {
        member();
        String known = mvc.perform(post("/members/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        clearInvocations(sender);
        String unknown = mvc.perform(post("/members/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing-" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(known, unknown);
        verifyNoInteractions(sender);
    }

    @Test
    void applicationPagesAndInputErrorsAreAvailableWithoutLogin() throws Exception {
        mvc.perform(get("/forgot-password.html")).andExpect(status().isOk());
        mvc.perform(get("/forgot-password.js")).andExpect(status().isOk());
        mvc.perform(post("/members/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }
}
