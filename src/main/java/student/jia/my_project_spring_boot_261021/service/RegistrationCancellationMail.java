package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Event;

/** 個別取消的 Email 佇列與雙管道排隊入口；提交後由各管道獨立寄送。 */
@Service
public class RegistrationCancellationMail {
    private static final DateTimeFormatter START_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CANCELLATION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final RegistrationCancellationLine lineQueue;
    public RegistrationCancellationMail(JdbcTemplate jdbc, MailService mail, RegistrationCancellationLine lineQueue) {
        this.lineQueue = lineQueue;
        this.jdbc = jdbc;
        this.mail = mail;
    }

    public void enqueue(Long cancellationId, Long memberId, Event event, String reason, String paymentNote) {
        String startTime = event.getStartAt().format(START_TIME_FORMAT);
        LocalDateTime cancelledAt = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        String cancellationTime = cancelledAt.format(CANCELLATION_TIME_FORMAT);
        String body = "你對活動「" + event.getTitle() + "」的報名已由管理員取消，整場活動並未因此取消。"
                + "\n原訂開始時間：" + startTime + "（台北時間）"
                + "\n取消時間：" + cancellationTime + "（台北時間）"
                + "\n取消原因：" + reason + "\n名額已釋出，若需要協助請聯絡管理員。";
        if (paymentNote != null) {
            body += "\n" + paymentNote;
        }
        jdbc.update("""
                INSERT INTO registration_cancellation_emails (cancellation_id, recipient, body)
                VALUES (?, (SELECT email FROM members WHERE id = ?), ?)
                """, cancellationId, memberId, body);
        lineQueue.enqueue(cancellationId, memberId, body);
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        List<Map<String, Object>> pending = jdbc.queryForList("""
                SELECT id, recipient, body FROM registration_cancellation_emails
                WHERE status = 'PENDING' ORDER BY id LIMIT 20
                """);
        for (Map<String, Object> row : pending) {
            Long id = ((Number) row.get("id")).longValue();
            int claimed = jdbc.update("UPDATE registration_cancellation_emails SET status = 'SENDING' WHERE id = ? AND status = 'PENDING'", id);
            if (claimed != 1) {
                continue;
            }
            String recipient = (String) row.get("recipient");
            String status = "SENT";
            if (recipient == null || recipient.isBlank()) {
                status = "FAILED";
            } else {
                try {
                    mail.sendEmail(recipient, "D&D 個別報名取消通知", (String) row.get("body"));
                } catch (RuntimeException failure) {
                    // SMTP 的結果可能不明，不自動重寄以避免重複通知。
                    status = "UNKNOWN";
                }
            }
            jdbc.update("UPDATE registration_cancellation_emails SET status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'SENDING'", status, id);
        }
    }

    public List<Map<String, Object>> list(Long eventId) {
        return jdbc.queryForList("""
                SELECT q.cancellation_id AS cancellationId, q.status, q.updated_at AS updatedAt, l.status AS lineStatus
                FROM registration_cancellation_emails q
                JOIN registration_cancellations c ON c.id = q.cancellation_id
                LEFT JOIN registration_cancellation_lines l ON l.cancellation_id = q.cancellation_id
                WHERE c.event_id = ? ORDER BY q.id DESC
                """, eventId);
    }
}
