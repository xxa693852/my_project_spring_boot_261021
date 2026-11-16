package student.jia.my_project_spring_boot_261021.response;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import lombok.Getter;
import student.jia.my_project_spring_boot_261021.entity.Event;

@Getter
public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private String hostName;
    private String location;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime registrationDeadline;
    private int capacity;
    private boolean beginnerFriendly;
    private String coverTheme;
    private List<String> tags;
    private String status;
    private boolean hiddenFromPublicList;
    private int registrationFee;

    // 新增：查詢時計算的資訊
    private long registeredCount;
    private long remainingSlots;
    private boolean canRegister;

    public EventResponse(Event event, long registeredCount, long activeHolds, boolean paymentsReady) {
        this.id = event.getId();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.hostName = event.getHostName();
        this.location = event.getLocation();
        this.startAt = event.getStartAt();
        this.endAt = event.getEndAt();
        this.registrationDeadline = event.getRegistrationDeadline();
        this.capacity = event.getCapacity();
        this.beginnerFriendly = event.isBeginnerFriendly();
        this.coverTheme = event.getCoverTheme();
        this.registrationFee = event.getRegistrationFee();
        if (event.getCustomTags().isEmpty()) {
            this.tags = List.of();
        } else {
            this.tags = List.of(event.getCustomTags().split("\n"));
        }
        this.status = event.getStatus();
        this.hiddenFromPublicList = event.isHiddenFromPublicList();

        // 有效報名人數
        this.registeredCount = registeredCount;

        // 剩餘名額，最低顯示 0
        this.remainingSlots =
                Math.max(0L, event.getCapacity() - registeredCount - activeHolds);

        // 活動開放、尚未截止，而且還有名額
        this.canRegister =
                "OPEN".equals(event.getStatus())
                && LocalDateTime.now(ZoneId.of("Asia/Taipei")).isBefore(
                        event.getRegistrationDeadline())
                && (this.registrationFee == 0 || paymentsReady)
                && this.remainingSlots > 0;
    }
}
