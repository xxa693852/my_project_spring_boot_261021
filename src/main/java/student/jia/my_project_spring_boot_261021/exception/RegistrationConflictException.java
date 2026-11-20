package student.jia.my_project_spring_boot_261021.exception;

import java.time.LocalDateTime;
import java.util.List;
import student.jia.my_project_spring_boot_261021.entity.Event;

/**
 * 會員報名的活動時段重疊時使用，保存衝突活動的名稱與起訖時間。
 * handleRegistrationConflict 回傳 HTTP 400，讓前端可以列出衝突活動。
 */
public class RegistrationConflictException extends IllegalArgumentException {
    private final List<Conflict> conflicts;

    public RegistrationConflictException(List<Event> events) {
        super("你已報名另一場時段重疊的活動");
        List<Conflict> details = new java.util.ArrayList<>();
        for (Event event : events) {
            details.add(new Conflict(event.getTitle(), event.getStartAt(), event.getEndAt()));
        }
        this.conflicts = List.copyOf(details);
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    public record Conflict(String title, LocalDateTime startAt, LocalDateTime endAt) {}
}
