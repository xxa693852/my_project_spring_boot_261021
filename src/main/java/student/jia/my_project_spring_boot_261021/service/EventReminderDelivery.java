package student.jia.my_project_spring_boot_261021.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;

@Service
public class EventReminderDelivery {
    private static final Logger logger = LoggerFactory.getLogger(EventReminderDelivery.class);
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 報名截止同時是玩家自行取消的截止時間。
    private static final String ELIGIBLE = """
            FROM events e
            JOIN registrations r ON r.event_id = e.id
            LEFT JOIN event_reminder_deliveries d ON d.registration_id = r.id
            WHERE e.status = 'OPEN' AND r.status = 'REGISTERED'
              AND d.registration_id IS NULL
              AND e.registration_deadline > ?
              AND CASE
                  WHEN r.registered_at <= LEAST(e.start_at - INTERVAL 24 HOUR,
                                               e.registration_deadline - INTERVAL 2 HOUR)
                      THEN LEAST(e.start_at - INTERVAL 24 HOUR, e.registration_deadline - INTERVAL 2 HOUR)
                  WHEN r.registered_at < e.registration_deadline - INTERVAL 2 HOUR
                      THEN e.registration_deadline - INTERVAL 2 HOUR
                  ELSE NULL
              END <= ?
            """;

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final CancellationNotificationDelivery notifications;
    private final boolean enabled;
    private final String baseUrl;

    public EventReminderDelivery(JdbcTemplate jdbc, MemberRepository members,
            CancellationNotificationDelivery notifications,
            @Value("${app.event-reminders.enabled:false}") boolean enabled,
            @Value("${app.base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.members = members;
        this.notifications = notifications;
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceFirst("/+$", "");
    }

    @Scheduled(fixedDelay = 60000)
    public void deliverPending() {
        if (enabled) {
            deliverDue(LocalDateTime.now(TAIPEI));
        }
    }

    void deliverDue(LocalDateTime now) {
        List<Long> dueIds = jdbc.queryForList("SELECT r.id " + ELIGIBLE
                + " ORDER BY e.registration_deadline, r.id LIMIT 20", Long.class, now, now);

        for (long id : dueIds) {
            try {
                deliverOne(id, now);
            } catch (RuntimeException failure) {
                logger.warn("Event reminder {} needs review ({})", id, failure.getClass().getSimpleName());
            }
        }
    }

    private void deliverOne(long id, LocalDateTime now) {
        List<Reminder> matches = jdbc.query("""
                SELECT r.member_id, r.registered_at, e.title, e.start_at, e.location, e.registration_deadline
                """ + ELIGIBLE + " AND r.id = ?",
                (row, index) -> new Reminder(row.getLong("member_id"),
                        row.getObject("registered_at", LocalDateTime.class), row.getString("title"),
                        row.getObject("start_at", LocalDateTime.class), row.getString("location"),
                        row.getObject("registration_deadline", LocalDateTime.class)),
                now, now, id);
        if (matches.isEmpty()) {
            return;
        }

        // 先取得唯一寄送權。結果不明時保留紀錄，以免重啟後重複寄出。
        try {
            jdbc.update("""
                    INSERT INTO event_reminder_deliveries (registration_id, status) VALUES (?, 'SENDING')
                    """, id);
        } catch (org.springframework.dao.DuplicateKeyException alreadyClaimed) {
            return;
        }

        try {
            sendClaimed(id, now, matches.get(0));
        } catch (RuntimeException failure) {
            finish(id, "UNKNOWN", null, "DELIVERY_NEEDS_REVIEW");
            throw failure;
        }
    }

    private void sendClaimed(long id, LocalDateTime now, Reminder reminder) {
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM registrations r JOIN events e ON e.id = r.event_id
                WHERE r.id = ? AND r.status = 'REGISTERED' AND e.status = 'OPEN'
                  AND r.registered_at = ? AND e.start_at = ? AND e.registration_deadline = ?
                  AND e.registration_deadline > ?
                """, Integer.class, id, reminder.registeredAt(), reminder.startAt(), reminder.deadline(), now);
        if (active == null || active == 0) {
            jdbc.update("DELETE FROM event_reminder_deliveries WHERE registration_id = ? AND status = 'SENDING'", id);
            return;
        }

        Optional<Member> recipient = members.findByIdAndStatus(reminder.memberId(), "ACTIVE");
        if (recipient.isEmpty()) {
            finish(id, "SKIPPED", null, "MEMBER_UNAVAILABLE");
            return;
        }
        String subject = "D&D 開團提醒";
        String text = "活動「" + reminder.title() + "」即將開團。"
                + "\n開團時間：" + TIME.format(reminder.startAt()) + "（台北時間）"
                + "\n地點：" + reminder.location()
                + "\nGoogle 地圖：https://www.google.com/maps/search/?api=1&query="
                + URLEncoder.encode(reminder.location(), StandardCharsets.UTF_8)
                + "\n取消截止：" + TIME.format(reminder.deadline()) + "（台北時間）"
                + "\n查看我的報名：" + baseUrl + "/my-registrations.html";
        CancellationNotificationDelivery.Result result = notifications.send(recipient.get(), subject, text);
        finish(id, result.status(), result.channel(), result.code());
    }

    private void finish(long id, String status, String channel, String code) {
        jdbc.update("""
                UPDATE event_reminder_deliveries SET status = ?, channel = ?, result_code = ?
                WHERE registration_id = ? AND status = 'SENDING'
                """, status, channel, code, id);
    }

    private record Reminder(long memberId, LocalDateTime registeredAt, String title, LocalDateTime startAt, String location,
                            LocalDateTime deadline) {}
}
