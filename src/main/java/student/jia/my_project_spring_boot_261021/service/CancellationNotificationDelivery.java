package student.jia.my_project_spring_boot_261021.service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.service.CancellationNotificationQueue.Pending;

@Service
public class CancellationNotificationDelivery {
    private static final Logger logger = LoggerFactory.getLogger(CancellationNotificationDelivery.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final CancellationNotificationQueue queue;
    private final MemberRepository members;
    private final LineNotificationClient line;
    private final MailService mail;

    public CancellationNotificationDelivery(CancellationNotificationQueue queue,
            MemberRepository members, LineNotificationClient line, MailService mail) {
        this.queue = queue;
        this.members = members;
        this.line = line;
        this.mail = mail;
    }

    @Scheduled(fixedDelay = 5000)
    public void deliverPending() {
        if (!queue.isEnabled()) {
            return;
        }
        for (Pending pending : queue.pending()) {
            if (!queue.claim(pending.id())) {
                continue;
            }
            try {
                deliver(pending);
            } catch (RuntimeException failure) {
                logger.warn("Cancellation notification {} needs review ({})",
                        pending.id(), failure.getClass().getSimpleName());
                queue.finish(pending.id(), "UNKNOWN", null, "DELIVERY_NEEDS_REVIEW");
            }
        }
    }

    private void deliver(Pending pending) {
        Optional<Member> recipient = members.findByIdAndStatus(pending.memberId(), "ACTIVE");
        if (recipient.isEmpty()) {
            queue.finish(pending.id(), "SKIPPED", null, "MEMBER_UNAVAILABLE");
            return;
        }
        Member member = recipient.get();
        String text = "活動「" + pending.title() + "」已取消。原訂時間 "
                + TIME.format(pending.startAt()) + "（台北時間）。請至「我的報名」查看。";

        String fallbackReason = null;
        if (member.getNotificationLineUserId() != null) {
            fallbackReason = tryLine(pending.id(), member.getNotificationLineUserId(), text);
            if (fallbackReason == null) {
                return;
            }
        }

        if (member.getEmail() == null || member.getEmail().isBlank()) {
            queue.finish(pending.id(), "FAILED", null, "EMAIL_UNAVAILABLE");
            return;
        }
        try {
            mail.sendEmail(member.getEmail(), "D&D 活動取消通知", text);
        } catch (org.springframework.mail.MailException failure) {
            queue.finish(pending.id(), "FAILED", "EMAIL", "EMAIL_SEND_ERROR");
            return;
        }
        queue.finish(pending.id(), "SENT", "EMAIL", fallbackReason);
    }

    // 回傳備援原因代表尚未送出 LINE，可安全改寄 Email。
    private String tryLine(long id, String userId, String text) {
        if (!line.isConfigured()) {
            return "LINE_NOT_CONFIGURED";
        }
        try {
            line.verifyRecipient(userId);
        } catch (RestClientException failure) {
            return "LINE_RECIPIENT_UNAVAILABLE";
        }
        try {
            line.push(userId, text);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 409) {
                return "LINE_REJECTED";
            }
            queue.finish(id, "UNKNOWN", "LINE", "LINE_DELIVERY_UNCONFIRMED");
            return null;
        } catch (RestClientException failure) {
            queue.finish(id, "UNKNOWN", "LINE", "LINE_DELIVERY_UNCONFIRMED");
            return null;
        }
        queue.finish(id, "SENT", "LINE", null);
        return null;
    }
}
