package student.jia.my_project_spring_boot_261021.service;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
/** 獨立交易保留失敗嘗試；回傳訊息，提交後才由呼叫端拋出例外。 */
@Service
public class CommentWriteLimiter {
 private final JdbcTemplate jdbc;
 private final boolean enabled;
 private final Clock clock;
 @Autowired
 public CommentWriteLimiter(JdbcTemplate jdbc, @Value("${app.comments.rate-limit.enabled:true}") boolean enabled) { this(jdbc, enabled, Clock.systemUTC()); }
 public CommentWriteLimiter(JdbcTemplate jdbc, boolean enabled, Clock clock) { this.jdbc=jdbc; this.enabled=enabled; this.clock=clock; }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public String attempt(Long memberId) {
  if (!enabled) return null;
  long now=clock.millis();
  Map<String,Object> row=lock(memberId,now);
  long start=((Number)row.get("window_start")).longValue();
  int count=((Number)row.get("attempts")).intValue();
  long previous=((Number)row.get("last_attempt")).longValue();
  if (now-start>=60000) { start=now; count=0; }
  jdbc.update("UPDATE comment_write_limits SET window_start=?,attempts=?,last_attempt=? WHERE member_id=?",start,Math.min(count+1,1000000),now,memberId);
  if (count>=10) return "留言嘗試次數過多，請稍後再試。";
  if (count>0 && now-previous<2000) return "留言操作太頻繁，請稍後再試。";
  return null;
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public String reserveWrite(Long memberId,boolean edit) {
  if (!enabled) return null;
  long now=clock.millis();
  Map<String,Object> row=lock(memberId,now);
  Number previous=(Number)row.get("last_write");
  long interval=edit?10000:30000;
  if (previous!=null && now-previous.longValue()<interval) return edit?"編輯留言須間隔 10 秒，請稍後再試。":"發表留言須間隔 30 秒，請稍後再試。";
  // 通過內容與權限檢查後預約寫入；後續失敗也保留冷卻，避免重試繞過。
  jdbc.update("UPDATE comment_write_limits SET last_write=? WHERE member_id=?",now,memberId);
  return null;
 }
 private Map<String,Object> lock(Long memberId,long now) {
  jdbc.update("INSERT INTO comment_write_limits(member_id,window_start,attempts,last_attempt) VALUES(?,?,0,?) ON DUPLICATE KEY UPDATE member_id=member_id",memberId,now,now);
  return jdbc.queryForMap("SELECT * FROM comment_write_limits WHERE member_id=? FOR UPDATE",memberId);
 }
}
