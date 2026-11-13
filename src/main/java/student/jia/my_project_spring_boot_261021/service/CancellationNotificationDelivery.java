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

        Result result = send(member, "D&D 活動取消通知", text);
        queue.finish(pending.id(), result.status(), result.channel(), result.code());
    }

    Result send(Member member, String subject, String text) {
        String fallbackReason = null;
        if (member.getNotificationLineUserId() != null) {
            Result lineResult = tryLine(member.getNotificationLineUserId(), text);
            if (!"FALLBACK".equals(lineResult.status())) {
                return lineResult;
            }
            fallbackReason = lineResult.code();
        }

        if (member.getEmail() == null || member.getEmail().isBlank()) {
            return new Result("FAILED", null, "EMAIL_UNAVAILABLE");
        }
        try {
            mail.sendEmail(member.getEmail(), subject, text);
        } catch (org.springframework.mail.MailException failure) {
            return new Result("FAILED", "EMAIL", "EMAIL_SEND_ERROR");
        }
        return new Result("SENT", "EMAIL", fallbackReason);
    }

    // 只有確認 LINE 未送出時才改寄 Email。
    private Result tryLine(String userId, String text) {
        if (!line.isConfigured()) {
            return new Result("FALLBACK", "LINE", "LINE_NOT_CONFIGURED");
        }
        try {
            line.verifyRecipient(userId);
        } catch (RestClientException failure) {
            return new Result("FALLBACK", "LINE", "LINE_RECIPIENT_UNAVAILABLE");
        }
        try {
            line.push(userId, text);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 409) {
                return new Result("FALLBACK", "LINE", "LINE_REJECTED");
            }
            return new Result("UNKNOWN", "LINE", "LINE_DELIVERY_UNCONFIRMED");
        } catch (RestClientException failure) {
            return new Result("UNKNOWN", "LINE", "LINE_DELIVERY_UNCONFIRMED");
        }
        return new Result("SENT", "LINE", null);
    }

    record Result(String status, String channel, String code) {}
}
