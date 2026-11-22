package student.jia.my_project_spring_boot_261021.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Payment;

/** 與退款登記共用交易保存通知；提交後各管道獨立領取，不自動重寄結果不明的通知。 */
@Service
public class RefundNotificationService {
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final LineNotificationClient line;

    public RefundNotificationService(JdbcTemplate jdbc, MailService mail, LineNotificationClient line) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.line = line;
    }

    public void enqueue(Payment payment, Long memberId, Long eventId) {
        String title = jdbc.queryForObject("SELECT title FROM events WHERE id = ?", String.class, eventId);
        String time = payment.getRefundedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String body = "活動「" + title + "」的報名費 NT$" + payment.getAmount()
                + "，管理員已確認於金流後台處理全額退款，並在本站登記完成。"
                + "\n訂單編號：" + payment.getMerchantOrderNo()
                + "\n本站登記時間：" + time + "（台北時間）"
                + "\n退刷反映於信用卡帳單的時間依金流平台及發卡銀行作業為準，本通知不代表款項已入帳。";
        // 不對外傳送管理員的內部退款說明或對帳資訊。
        jdbc.update("""
                INSERT INTO refund_notifications (payment_id, channel, recipient, body, status)
                SELECT ?, 'EMAIL', email, ?, 'PENDING' FROM members WHERE id = ?
                """, payment.getId(), body, memberId);
        jdbc.update("""
                INSERT INTO refund_notifications (payment_id, channel, recipient, body, status)
                SELECT ?, 'LINE', notification_line_user_id, ?,
                    CASE WHEN notification_line_user_id IS NULL OR notification_line_user_id = ''
                    THEN 'NOT_BOUND' ELSE 'PENDING' END
                FROM members WHERE id = ?
                """, payment.getId(), body, memberId);
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, channel, recipient, body FROM refund_notifications
                WHERE status = 'PENDING' ORDER BY id LIMIT 20
                """);
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            int claimed = jdbc.update("UPDATE refund_notifications SET status = 'SENDING' WHERE id = ? AND status = 'PENDING'", id);
            if (claimed != 1) {
                continue;
            }
            String status = "SENT";
            String recipient = (String) row.get("recipient");
            try {
                if (recipient == null || recipient.isBlank()) {
                    status = "FAILED";
                } else if ("EMAIL".equals(row.get("channel"))) {
                    mail.sendEmail(recipient, "D&D 退款處理通知", (String) row.get("body"));
                } else if (!line.isConfigured()) {
                    status = "NOT_CONFIGURED";
                } else {
                    line.verifyRecipient(recipient);
                    line.push(recipient, (String) row.get("body"));
                }
            } catch (RuntimeException failure) {
                status = "UNKNOWN";
            }
            jdbc.update("UPDATE refund_notifications SET status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'SENDING'", status, id);
        }
    }

    public List<Map<String, Object>> list(Long eventId) {
        return jdbc.queryForList("""
                SELECT n.payment_id AS paymentId, n.channel, n.status FROM refund_notifications n
                JOIN payments p ON p.id = n.payment_id JOIN registrations r ON r.id = p.registration_id
                WHERE r.event_id = ? ORDER BY n.id
                """, eventId);
    }
}
