package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import student.jia.my_project_spring_boot_261021.entity.Event;

@Service
public class CancellationNotificationQueue {
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public CancellationNotificationQueue(JdbcTemplate jdbc,
            @Value("${app.cancellation-notifications.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enqueue(Event event, Long memberId) {
        if (!enabled) {
            return;
        }
        // JDBC 與取消活動共用交易，提交成功後排程才會看到通知。
        jdbc.update("""
                INSERT INTO event_cancellation_notifications
                    (event_id, member_id, event_title, event_start_at)
                VALUES (?, ?, ?, ?)
                """, event.getId(), memberId, event.getTitle(), event.getStartAt());
    }

    public List<Pending> pending() {
        return jdbc.query("""
                SELECT id, member_id, event_title, event_start_at
                FROM event_cancellation_notifications
                WHERE status = 'PENDING' ORDER BY id LIMIT 20
                """, (row, index) -> new Pending(row.getLong("id"), row.getLong("member_id"),
                row.getString("event_title"), row.getObject("event_start_at", LocalDateTime.class)));
    }

    public boolean claim(long id) {
        int updated = jdbc.update("""
                UPDATE event_cancellation_notifications
                SET status = 'SENDING', updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'PENDING'
                """, id);
        return updated == 1;
    }

    public void finish(long id, String status, String channel, String code) {
        jdbc.update("""
                UPDATE event_cancellation_notifications
                SET status = ?, channel = ?, result_code = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'SENDING'
                """, status, channel, code, id);
    }

    public List<Delivery> findByEvent(long eventId) {
        return jdbc.query("""
                SELECT member_id, status, channel, result_code, updated_at
                FROM event_cancellation_notifications WHERE event_id = ? ORDER BY id
                """, (row, index) -> new Delivery(row.getLong("member_id"), row.getString("status"),
                row.getString("channel"), row.getString("result_code"),
                row.getObject("updated_at", LocalDateTime.class)), eventId);
    }

    record Pending(long id, long memberId, String title, LocalDateTime startAt) {}

    public record Delivery(long memberId, String status, String channel, String resultCode,
            LocalDateTime updatedAt) {}
}
