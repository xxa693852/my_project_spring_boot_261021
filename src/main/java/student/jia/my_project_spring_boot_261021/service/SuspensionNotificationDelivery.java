package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;

@Service
public class SuspensionNotificationDelivery {
    private static final Logger logger = LoggerFactory.getLogger(SuspensionNotificationDelivery.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final CancellationNotificationDelivery notifications;

    public SuspensionNotificationDelivery(JdbcTemplate jdbc, MemberRepository members,
            CancellationNotificationDelivery notifications) {
        this.jdbc = jdbc;
        this.members = members;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        List<Pending> pending = jdbc.query("""
                SELECT id, member_id, reason, suspended_until
                FROM member_suspension_actions
                WHERE notification_status = 'PENDING' ORDER BY id LIMIT 20
                """, (row, index) -> new Pending(row.getLong("id"), row.getLong("member_id"),
                row.getString("reason"), row.getObject("suspended_until", LocalDateTime.class)));
        for (Pending action : pending) {
            int claimed = jdbc.update("""
                    UPDATE member_suspension_actions SET notification_status = 'SENDING'
                    WHERE id = ? AND notification_status = 'PENDING'
                    """, action.id());
            if (claimed != 1) {
                continue;
            }
            try {
                deliver(action);
            } catch (RuntimeException failure) {
                logger.warn("Suspension notification {} needs review ({})", action.id(),
                        failure.getClass().getSimpleName());
                finish(action.id(), "UNKNOWN", null, "DELIVERY_NEEDS_REVIEW");
            }
        }
    }

    private void deliver(Pending action) {
        Member member = members.findByIdAndStatus(action.memberId(), "ACTIVE").orElse(null);
        if (member == null) {
            finish(action.id(), "SKIPPED", null, "MEMBER_UNAVAILABLE");
            return;
        }
        String duration = "永久停權";
        if (action.until() != null) {
            duration = "停權至 " + TIME.format(action.until()) + "（台北時間）";
        }
        String text = "你的 D&D 活動報名帳號已被停權。\n"
                + "期限：" + duration + "\n原因：" + action.reason()
                + "\n停權期間無法報名新活動，仍可查看及依原期限取消既有報名。";
        CancellationNotificationDelivery.Result result = notifications.send(member, "D&D 帳號停權通知", text);
        finish(action.id(), result.status(), result.channel(), result.code());
    }

    private void finish(long id, String status, String channel, String code) {
        jdbc.update("""
                UPDATE member_suspension_actions
                SET notification_status = ?, notification_channel = ?, notification_result_code = ?
                WHERE id = ? AND notification_status = 'SENDING'
                """, status, channel, code, id);
    }

    private record Pending(long id, long memberId, String reason, LocalDateTime until) {}
}
