package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.EventComment;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.EventCommentRepository;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;

@Service
public class EventCommentService {

    public static final int BODY_LIMIT = 500;
    public static final int REASON_LIMIT = 200;
    private static final int PAGE_SIZE = 20;
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final EventService eventService;
    private final EventRepository events;
    private final EventCommentRepository comments;
    private final MemberRepository members;
    private final CommentModerationNotifications moderationNotifications;

    public EventCommentService(EventService eventService, EventRepository events,
            EventCommentRepository comments, MemberRepository members,
            CommentModerationNotifications moderationNotifications) {
        this.eventService = eventService;
        this.events = events;
        this.comments = comments;
        this.members = members;
        this.moderationNotifications = moderationNotifications;
    }

    @Transactional(readOnly = true)
    public CommentPage list(Long eventId, Long memberId, int page) {
        if (page < 1 || page > Integer.MAX_VALUE / PAGE_SIZE) {
            throw new IllegalArgumentException("留言頁碼超出範圍。");
        }
        Event event = eventService.findPublicEventById(eventId);
        LocalDateTime now = LocalDateTime.now(TAIPEI);
        boolean open = acceptsComments(event, now);
        Member member = null;
        if (memberId != null) {
            member = members.findByIdAndStatus(memberId, "ACTIVE").orElse(null);
        }
        boolean canWrite = member != null && !member.isSuspendedAt(now);
        boolean admin = canWrite && "ADMIN".equals(member.getRole());

        PageRequest request = PageRequest.of(page - 1, PAGE_SIZE);
        Page<EventComment> results;
        if (admin) {
            results = comments.findByEventIdOrderByIdDesc(eventId, request);
        } else {
            results = comments.findByEventIdAndHiddenReasonIsNullOrderByIdDesc(eventId, request);
        }
        List<CommentView> items = new ArrayList<>();
        for (EventComment comment : results) {
            String reason = null;
            if (admin) {
                reason = comment.getHiddenReason();
            }
            items.add(new CommentView(comment.getId(), comment.getAuthor().getName(),
                    comment.getBody(), comment.getCreatedAt(),
                    open && canWrite && memberId.equals(comment.getAuthor().getId()), reason));
        }
        return new CommentPage(items, open, open && canWrite, admin, results.hasNext(), results.getTotalPages());
    }

    @Transactional
    public void post(Long eventId, Long memberId, String body) {
        Member member = activeOperator(memberId);
        Event event = lockEvent(eventId);
        LocalDateTime now = LocalDateTime.now(TAIPEI);
        requireOpen(event, now);
        comments.save(new EventComment(eventId, member, body.strip(), now));
    }

    @Transactional
    public void delete(Long eventId, Long commentId, Long memberId) {
        activeOperator(memberId);
        Event event = lockEvent(eventId);
        requireOpen(event, LocalDateTime.now(TAIPEI));
        EventComment comment = lockComment(eventId, commentId);
        if (!comment.getAuthor().getId().equals(memberId)) {
            throw commentNotFound();
        }
        comments.delete(comment);
    }

    @Transactional
    public void moderate(Long eventId, Long commentId, Long memberId, String reason) {
        Member operator = activeOperator(memberId);
        if (!"ADMIN".equals(operator.getRole())) {
            throw new IllegalArgumentException("只有管理員可以管理留言。");
        }
        Event event = lockEvent(eventId);
        EventComment comment = lockComment(eventId, commentId);
        boolean alreadyHidden = comment.getHiddenReason() != null;
        if (reason == null) {
            if (!alreadyHidden) {
                return;
            }
            comment.restore();
        } else {
            if (alreadyHidden) {
                return;
            }
            reason = reason.strip();
            comment.hide(reason);
        }
        moderationNotifications.enqueue(comment.getAuthor().getId(), comment.getId(),
                event.getTitle(), comment.getCreatedAt(), reason);
    }

    private Member activeOperator(Long memberId) {
        Member member = members.findActiveByIdForUpdate(memberId)
                .orElseThrow(this::memberNotFound);
        if (member.isSuspendedAt(LocalDateTime.now(TAIPEI))) {
            throw new IllegalArgumentException("帳號停權期間無法操作留言。");
        }
        return member;
    }

    private ResourceNotFoundException memberNotFound() {
        return new ResourceNotFoundException("member", ResponseMessages.MEMBER_NOT_FOUND);
    }

    private Event lockEvent(Long eventId) {
        Event event = events.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !EventService.isPublicEvent(event)) {
            throw new ResourceNotFoundException("event", ResponseMessages.EVENT_NOT_FOUND);
        }
        return event;
    }

    private boolean acceptsComments(Event event, LocalDateTime now) {
        return !"CANCELLED".equals(event.getStatus()) && now.isBefore(event.getEndAt());
    }

    private void requireOpen(Event event, LocalDateTime now) {
        if (!acceptsComments(event, now)) {
            throw new IllegalArgumentException("活動已取消或結束，留言區僅供查看。");
        }
    }

    private EventComment lockComment(Long eventId, Long commentId) {
        return comments.findForUpdate(eventId, commentId).orElseThrow(this::commentNotFound);
    }

    private ResourceNotFoundException commentNotFound() {
        return new ResourceNotFoundException("comment", "找不到這則留言。");
    }

    public record CommentView(Long id, String authorName, String body, LocalDateTime createdAt,
            boolean canDelete, String hiddenReason) {
    }

    public record CommentPage(List<CommentView> comments, boolean open, boolean canPost,
            boolean canModerate, boolean hasNext, int totalPages) {
    }
}
