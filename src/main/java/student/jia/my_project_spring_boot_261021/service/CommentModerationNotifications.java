package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
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
public class CommentModerationNotifications {

    private static final Logger logger = LoggerFactory.getLogger(CommentModerationNotifications.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final CancellationNotificationDelivery notifications;
    private final String supportEmail;

    public CommentModerationNotifications(JdbcTemplate jdbc, MemberRepository members,
            CancellationNotificationDelivery notifications,
            @Value("${COMMENT_MODERATION_SUPPORT_EMAIL:${GMAIL_USERNAME:}}") String supportEmail) {
        this.jdbc = jdbc;
        this.members = members;
        this.notifications = notifications;
        this.supportEmail = supportEmail.strip();
    }

    // 與留言狀態在同一個交易內儲存，提交後排程才會讀到通知。
    void enqueue(long memberId, long commentId, String eventTitle, LocalDateTime commentCreatedAt, String reason) {
        jdbc.update("""
                INSERT INTO comment_moderation_notifications
                    (member_id, comment_id, event_title, comment_created_at, reason, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING')
                """, memberId, commentId, eventTitle, commentCreatedAt, reason);
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        List<Pending> pending = jdbc.query("""
                SELECT id, member_id, comment_id, event_title, comment_created_at, reason
                FROM comment_moderation_notifications WHERE status = 'PENDING'
                ORDER BY id LIMIT 20
                """, (row, index) -> new Pending(row.getLong("id"), row.getLong("member_id"),
                row.getLong("comment_id"), row.getString("event_title"),
                row.getObject("comment_created_at", LocalDateTime.class), row.getString("reason")));
        for (Pending notice : pending) {
            int claimed = jdbc.update("""
                    UPDATE comment_moderation_notifications SET status = 'SENDING'
                    WHERE id = ? AND status = 'PENDING'
                    """, notice.id());
            if (claimed != 1) {
                continue;
            }
            try {
                deliver(notice);
            } catch (RuntimeException failure) {
                logger.warn("Comment moderation notification {} needs review ({})",
                        notice.id(), failure.getClass().getSimpleName());
                finish(notice.id(), "UNKNOWN", null, "DELIVERY_NEEDS_REVIEW");
            }
        }
    }

    private void deliver(Pending notice) {
        Optional<Member> recipient = members.findByIdAndStatus(notice.memberId(), "ACTIVE");
        if (recipient.isEmpty()) {
            finish(notice.id(), "SKIPPED", null, "MEMBER_UNAVAILABLE");
            return;
        }

        String outcome = "已恢復公開";
        if (notice.reason() != null) {
            outcome = "已隱藏";
        }
        String subject = "D&D 留言" + outcome;
        String text = "你在活動「" + notice.eventTitle() + "」的留言" + outcome + "。\n"
                + "留言時間：" + TIME.format(notice.commentCreatedAt()) + "（台北時間）\n"
                + "留言編號：" + notice.commentId();
        if (notice.reason() != null) {
            text += "\n原因：" + notice.reason();
        }
        if (!supportEmail.isBlank()) {
            text += "\n如有疑問，請聯絡：" + supportEmail;
        }

        CancellationNotificationDelivery.Result result = notifications.send(recipient.get(), subject, text);
        finish(notice.id(), result.status(), result.channel(), result.code());
    }

    private void finish(long id, String status, String channel, String code) {
        jdbc.update("""
                UPDATE comment_moderation_notifications SET status = ?, channel = ?, result_code = ?
                WHERE id = ? AND status = 'SENDING'
                """, status, channel, code, id);
    }

    private record Pending(long id, long memberId, long commentId, String eventTitle,
            LocalDateTime commentCreatedAt, String reason) {}
}
