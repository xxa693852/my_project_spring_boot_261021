package student.jia.my_project_spring_boot_261021.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 個別取消的 LINE 佇列；與 Email 分開領取，不因單一管道失敗而省略另一管道。 */
@Service
public class RegistrationCancellationLine {
    private final JdbcTemplate jdbc;
    private final LineNotificationClient line;

    public RegistrationCancellationLine(JdbcTemplate jdbc, LineNotificationClient line) {
        this.jdbc = jdbc;
        this.line = line;
    }

    // 與取消報名共用交易，保存取消當時的綁定對象與訊息快照。
    public void enqueue(Long cancellationId, Long memberId, String body) {
        jdbc.update("""
                INSERT INTO registration_cancellation_lines (cancellation_id, recipient, body, status)
                SELECT ?, notification_line_user_id, ?,
                    CASE WHEN notification_line_user_id IS NULL OR notification_line_user_id = ''
                    THEN 'NOT_BOUND' ELSE 'PENDING' END
                FROM members WHERE id = ?
                """, cancellationId, body, memberId);
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        List<Map<String, Object>> pending = jdbc.queryForList("""
                SELECT id, recipient, body FROM registration_cancellation_lines
                WHERE status = 'PENDING' ORDER BY id LIMIT 20
                """);
        for (Map<String, Object> row : pending) {
            Long id = ((Number) row.get("id")).longValue();
            int claimed = jdbc.update("UPDATE registration_cancellation_lines SET status = 'SENDING' WHERE id = ? AND status = 'PENDING'", id);
            if (claimed != 1) {
                continue;
            }
            String status;
            try {
                status = send((String) row.get("recipient"), (String) row.get("body"));
            } catch (RuntimeException failure) {
                // 結果不明不重寄；可在後台人工確認，避免重複推播。
                status = "UNKNOWN";
            }
            jdbc.update("UPDATE registration_cancellation_lines SET status = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'SENDING'", status, id);
        }
    }

    private String send(String recipient, String body) {
        if (recipient == null || recipient.isBlank()) {
            return "NOT_BOUND";
        }
        if (!line.isConfigured()) {
            return "NOT_CONFIGURED";
        }
        try {
            line.verifyRecipient(recipient);
        } catch (RestClientException failure) {
            return "RECIPIENT_ERROR";
        }
        try {
            line.push(recipient, body);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 409) {
                return "REJECTED";
            }
            return "UNKNOWN";
        } catch (RestClientException failure) {
            return "UNKNOWN";
        }
        return "SENT";
    }
}
