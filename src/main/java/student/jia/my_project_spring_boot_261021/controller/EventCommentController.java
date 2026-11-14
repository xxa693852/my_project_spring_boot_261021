package student.jia.my_project_spring_boot_261021.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.service.EventCommentService;
import student.jia.my_project_spring_boot_261021.service.EventCommentService.CommentPage;

@RestController
public class EventCommentController {

    private static final String PUBLIC = "/events/{eventId}/comments";
    private static final String MEMBER = "/members/me/events/{eventId}/comments";
    private static final String MODERATION = "/admin/events/{eventId}/comments/{commentId}/visibility";
    private static final String MEMBER_ID = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE;

    private final EventCommentService service;

    public EventCommentController(EventCommentService service) {
        this.service = service;
    }

    @GetMapping(PUBLIC)
    public ResponseEntity<CommentPage> publicComments(@PathVariable Long eventId,
            @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(eventId, null, page));
    }

    @GetMapping(MEMBER)
    public ResponseEntity<CommentPage> memberComments(@PathVariable Long eventId,
            @RequestAttribute(MEMBER_ID) Long memberId, @RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.list(eventId, memberId, page));
    }

    @PostMapping(MEMBER)
    @ResponseStatus(HttpStatus.CREATED)
    public void post(@PathVariable Long eventId, @RequestAttribute(MEMBER_ID) Long memberId,
            @Valid @RequestBody PostComment request) {
        service.post(eventId, memberId, request.body());
    }

    @DeleteMapping(MEMBER + "/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long eventId, @PathVariable Long commentId,
            @RequestAttribute(MEMBER_ID) Long memberId) {
        service.delete(eventId, commentId, memberId);
    }

    @PutMapping(MODERATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable Long eventId, @PathVariable Long commentId,
            @RequestAttribute(MEMBER_ID) Long memberId, @Valid @RequestBody HideComment request) {
        service.moderate(eventId, commentId, memberId, request.reason());
    }

    @DeleteMapping(MODERATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long eventId, @PathVariable Long commentId,
            @RequestAttribute(MEMBER_ID) Long memberId) {
        service.moderate(eventId, commentId, memberId, null);
    }

    public record PostComment(
            @NotBlank(message = "請輸入留言。")
            @Size(max = EventCommentService.BODY_LIMIT, message = "留言最多 500 字。")
            String body) {
    }

    public record HideComment(
            @NotBlank(message = "請輸入隱藏原因。")
            @Size(max = EventCommentService.REASON_LIMIT, message = "隱藏原因最多 200 字。")
            String reason) {
    }
}
