package student.jia.my_project_spring_boot_261021.controller;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.service.CancellationNotificationQueue;
import student.jia.my_project_spring_boot_261021.service.LineNotificationClient;

@RestController
@RequestMapping("/members/me/line-notifications")
public class LineNotificationController {
    private static final String PENDING_LINK = "line.notification.pending";
    private final MemberRepository members;
    private final LineNotificationClient line;
    private final CancellationNotificationQueue queue;

    public LineNotificationController(MemberRepository members, LineNotificationClient line,
            CancellationNotificationQueue queue) {
        this.members = members;
        this.line = line;
        this.queue = queue;
    }

    @GetMapping
    public LinkStatus status(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId) {
        Member member = requireActiveMember(memberId);
        return new LinkStatus(queue.isEnabled(), line.isConfigured(), member.getNotificationLineUserId() != null);
    }

    @PostMapping("/start")
    public LinkStart start(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            HttpSession session) {
        requireActiveMember(memberId);
        requireAvailable();
        PendingLink pending = new PendingLink(memberId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), Instant.now().plusSeconds(600));
        synchronized (session) {
            session.setAttribute(PENDING_LINK, pending);
        }
        return new LinkStart(line.authorizationUrl(pending.state(), pending.nonce()));
    }

    @PostMapping("/complete")
    public LinkStatus complete(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            HttpSession session, @Valid @RequestBody LinkCallback callback) {
        requireActiveMember(memberId);
        requireAvailable();
        PendingLink pending;
        synchronized (session) {
            pending = (PendingLink) session.getAttribute(PENDING_LINK);
            if (pending == null || !pending.memberId().equals(memberId)
                    || !pending.state().equals(callback.state())
                    || !Instant.now().isBefore(pending.expiresAt())) {
                throw new IllegalArgumentException("LINE 綁定驗證已失效，請重新開始");
            }
            session.removeAttribute(PENDING_LINK);
        }

        String lineUserId;
        try {
            lineUserId = line.verifyAndGetFriend(callback.code(), pending.nonce());
        } catch (RestClientException failure) {
            throw new IllegalArgumentException("目前無法驗證 LINE 帳號，請重新綁定");
        }
        try {
            updateLink(memberId, lineUserId);
        } catch (DataIntegrityViolationException failure) {
            throw new IllegalArgumentException("此 LINE 帳號已綁定其他會員");
        }
        return new LinkStatus(true, true, true);
    }

    @DeleteMapping
    public LinkStatus unlink(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            HttpSession session) {
        synchronized (session) {
            session.removeAttribute(PENDING_LINK);
        }
        updateLink(memberId, null);
        return new LinkStatus(queue.isEnabled(), line.isConfigured(), false);
    }

    private Member requireActiveMember(Long memberId) {
        Optional<Member> member = members.findByIdAndStatus(memberId, "ACTIVE");
        if (member.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return member.get();
    }

    private void updateLink(Long memberId, String lineUserId) {
        if (members.updateNotificationLineUserId(memberId, lineUserId) != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireAvailable() {
        if (!queue.isEnabled() || !line.isConfigured()) {
            throw new IllegalArgumentException("LINE 通知尚未開放");
        }
    }

    private record PendingLink(Long memberId, String state, String nonce, Instant expiresAt) {}
    public record LinkStatus(boolean notificationsEnabled, boolean lineAvailable, boolean linked) {}
    public record LinkStart(String authorizationUrl) {}
    public record LinkCallback(@NotBlank @Size(max = 512) String code,
            @NotBlank @Size(max = 64) String state) {}
}
