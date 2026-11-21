package student.jia.my_project_spring_boot_261021;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.show-sql=false","app.comments.rate-limit.enabled=true"})
@Transactional
class CommentRateLimitTests {
 @Autowired WebApplicationContext context;
 @Autowired EntityManager em;
 @Autowired JdbcTemplate jdbc;
 @Autowired MemberRepository members;
 @Autowired EventRepository events;
 @Autowired PasswordEncoder encoder;
 @Autowired CommentWriteLimiter limiter;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
 MockMvc mvc;
 Clock clock;
 long now;
 Member author;
 String token;
 Event first,second;
 @BeforeEach void prepare() throws Exception {
  assertEquals("dnd_event_prod_test", em.createNativeQuery("SELECT DATABASE()").getSingleResult());
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  clock=mock(Clock.class); now=Instant.now().toEpochMilli();
  when(clock.millis()).thenAnswer(invocation -> now);
  ReflectionTestUtils.setField(limiter,"clock",clock);
  String account="rate_"+UUID.randomUUID().toString().substring(0,12);
  author=members.saveAndFlush(new Member(account,encoder.encode("RatePassword123!"),"測試",account+"@example.com"));
  String json=mvc.perform(post("/members/login").contentType("application/json").content("{\"account\":\""+account+"\",\"password\":\"RatePassword123!\"}"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  token="Bearer "+com.jayway.jsonpath.JsonPath.read(json,"$.accessToken");
  LocalDateTime start=LocalDateTime.now(ZoneId.of("Asia/Taipei")).plusDays(2);
  first=events.saveAndFlush(new Event("限流一","介紹","主持","台北",start,start.plusHours(3),start.minusHours(1),6,true,author.getId()));
  second=events.saveAndFlush(new Event("限流二","介紹","主持","台北",start,start.plusHours(3),start.minusHours(1),6,true,author.getId()));
 }
 @AfterEach void resetClock() { ReflectionTestUtils.setField(limiter,"clock",Clock.systemUTC()); }
 @AfterTransaction void cleanRateRows() { jdbc.update("DELETE FROM comment_write_limits WHERE member_id=?",author.getId()); }
 String path(Event event) { return "/members/me/events/"+event.getId()+"/comments"; }
 String body(String value) { return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("body",value)); }
 long commentId(Event event) { return jdbc.queryForObject("SELECT id FROM event_comments WHERE event_id=?",Long.class,event.getId()); }
 @Test void cooldownSurvivesDeletionAndAppliesAcrossEventsAndEdits() throws Exception {
  mvc.perform(post(path(first)).header("Authorization",token).contentType("application/json").content(body("屠龍戰鬥與邪教，殺死不死生物"))).andExpect(status().isCreated());
  mvc.perform(delete(path(first)+"/"+commentId(first)).header("Authorization",token)).andExpect(status().isNoContent());
  now+=2000;
  mvc.perform(post(path(second)).header("Authorization",token).contentType("application/json").content(body("正常內容"))).andExpect(status().isTooManyRequests());
  now+=28000;
  mvc.perform(post(path(second)).header("Authorization",token).contentType("application/json").content(body("正常內容"))).andExpect(status().isCreated());
  long id=commentId(second);
  now+=2000;
  mvc.perform(put(path(second)+"/"+id).header("Authorization",token).contentType("application/json").content(body("編輯草稿"))).andExpect(status().isTooManyRequests());
  assertEquals("正常內容",jdbc.queryForObject("SELECT body FROM event_comments WHERE id=?",String.class,id));
  now+=8000;
  mvc.perform(put(path(second)+"/"+id).header("Authorization",token).contentType("application/json").content(body("更新正常內容"))).andExpect(status().isNoContent());
  em.flush();
  assertEquals("更新正常內容",jdbc.queryForObject("SELECT body FROM event_comments WHERE id=?",String.class,id));
  now+=2000;
  mvc.perform(put(path(second)+"/"+id).header("Authorization",token).contentType("application/json").content(body("操你媽"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PublicContentPolicy.REJECTION)).andExpect(jsonPath("$.errors").doesNotExist());
  assertEquals("更新正常內容",jdbc.queryForObject("SELECT body FROM event_comments WHERE id=?",String.class,id));
 }
 @Test void failedValidationAttemptsPersistAndHitMinuteLimitAcrossEvents() throws Exception {
  for(int i=0;i<10;i++) {
   mvc.perform(post(path(i%2==0?first:second)).header("Authorization",token).contentType("application/json").content(body("字".repeat(501)))).andExpect(status().isBadRequest());
   now+=2000;
  }
  mvc.perform(post(path(second)).header("Authorization",token).contentType("application/json").content(body("正常留言"))).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value("留言嘗試次數過多，請稍後再試。"));
  // 外層 MySQL REPEATABLE READ 的快照早於獨立限流交易；用新交易查詢提交後的狀態。
  var read = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
  read.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  Integer attempts = read.execute(status -> jdbc.queryForObject("SELECT attempts FROM comment_write_limits WHERE member_id=?",Integer.class,author.getId()));
  assertEquals(Integer.valueOf(11), attempts);
  now+=60000;
  mvc.perform(post(path(first)).header("Authorization",token).contentType("application/json").content(body("新一輪正常留言"))).andExpect(status().isCreated());
 }
 @Test void sensitiveRejectionConsumesAttemptButDoesNotPublishOrExposeMatch() throws Exception {
  mvc.perform(post(path(first)).header("Authorization",token).contentType("application/json").content(body("操你媽"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(PublicContentPolicy.REJECTION));
  mvc.perform(post(path(second)).header("Authorization",token).contentType("application/json").content(body("正常留言"))).andExpect(status().isTooManyRequests());
  now+=2000;
  mvc.perform(post(path(second)).header("Authorization",token).contentType("application/json").content(body("正常留言"))).andExpect(status().isCreated());
 }

}
