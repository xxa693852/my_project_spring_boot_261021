package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;

@Service
public class AdminMemberService {
    private final MemberRepository members;
    private final RegistrationService registrations;
    private final JdbcTemplate jdbc;

    public AdminMemberService(MemberRepository members, RegistrationService registrations, JdbcTemplate jdbc) {
        this.members = members;
        this.registrations = registrations;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public MemberPage search(Long actor, String query, int page) {
        requireAdmin(actor);
        if (page < 1 || page > Integer.MAX_VALUE / 20 || query.length() > 254) {
            throw new IllegalArgumentException("搜尋條件或頁碼不正確");
        }
        String term = query.strip();
        PageRequest request = PageRequest.of(page - 1, 20, Sort.by(Sort.Direction.DESC, "id"));
        Page<Member> results = members.findByAccountContainingOrNameContainingOrEmailContaining(term, term, term, request);
        LocalDateTime now = now();
        List<MemberSummary> items = new ArrayList<>();
        for (Member member : results) {
            items.add(summary(member, now));
        }
        return new MemberPage(items, page, results.getTotalPages(), results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MemberDetails details(Long actor, Long id) {
        requireAdmin(actor);
        Member member = members.findById(id).orElse(null);
        if (member == null) {
            throw new ResourceNotFoundException("member", ResponseMessages.MEMBER_NOT_FOUND);
        }
        List<SuspensionAction> actions = jdbc.query("""
                SELECT actor_id, action, reason, acted_at, suspended_until, notification_status, notification_channel, notification_result_code
                FROM member_suspension_actions WHERE member_id = ? ORDER BY id DESC
                """, (row, index) -> new SuspensionAction(row.getLong("actor_id"), row.getString("action"),
                row.getString("reason"), row.getObject("acted_at", LocalDateTime.class),
                row.getObject("suspended_until", LocalDateTime.class), row.getString("notification_status"),
                row.getString("notification_channel"), row.getString("notification_result_code")), id);
        return new MemberDetails(summary(member, now()), registrations.findMyRegistrations(id), actions);
    }

    @Transactional
    public void changeSuspension(Long actor, Long id, String action, LocalDateTime until, String reason) {
        requireAdmin(actor);
        if (actor.equals(id)) {
            throw new IllegalArgumentException("不能停權自己的帳號");
        }
        Member member = members.findActiveByIdForUpdate(id).orElse(null);
        if (member == null) {
            throw new ResourceNotFoundException("member", ResponseMessages.MEMBER_NOT_FOUND);
        }
        if (!"MEMBER".equals(member.getRole())) {
            throw new IllegalArgumentException("僅能管理一般會員的停權");
        }
        LocalDateTime now = now();
        if ("LIFT".equals(action)) {
            if (!member.isSuspendedAt(now) || until != null) {
                throw new IllegalArgumentException("此會員沒有可解除的停權");
            }
            member.setSuspendedAt(null);
            member.setSuspendedUntil(null);
        } else if ("SUSPEND".equals(action)) {
            if (until != null && !until.isAfter(now)) {
                throw new IllegalArgumentException("停權截止時間必須晚於現在");
            }
            member.setSuspendedAt(now);
            member.setSuspendedUntil(until);
        } else {
            throw new IllegalArgumentException("無效的停權操作");
        }

        jdbc.update("""
                INSERT INTO member_suspension_actions
                    (member_id, actor_id, action, reason, acted_at, suspended_until, notification_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, actor, action, reason.strip(), now, until, "SUSPEND".equals(action) ? "PENDING" : null);
    }

    private void requireAdmin(Long actor) {
        Member member = members.findByIdAndStatus(actor, "ACTIVE").orElse(null);
        if (member == null || !"ADMIN".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理員可以管理會員");
        }
    }

    private MemberSummary summary(Member member, LocalDateTime now) {
        return new MemberSummary(member.getId(), member.getAccount(), member.getName(), member.getEmail(),
                member.getRole(), member.getStatus(), member.getCreatedAt(), member.isSuspendedAt(now),
                member.getSuspendedUntil());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Taipei"));
    }

    public record MemberSummary(Long id, String account, String name, String email, String role,
            String status, LocalDateTime createdAt, boolean suspended, LocalDateTime suspendedUntil) {}
    public record MemberPage(List<MemberSummary> items, int page, int totalPages, long totalElements) {}
    public record MemberDetails(MemberSummary member, List<RegistrationResponse> registrations,
            List<SuspensionAction> actions) {}
    public record SuspensionAction(long actorId, String action, String reason, LocalDateTime actedAt,
            LocalDateTime suspendedUntil, String notificationStatus, String notificationChannel,
            String notificationResultCode) {}
}
