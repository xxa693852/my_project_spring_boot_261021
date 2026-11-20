package student.jia.my_project_spring_boot_261021.response;

import java.time.LocalDateTime;
import java.time.ZoneId;

import lombok.Getter;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.entity.Event;

/**
 * 將報名、活動與付款狀態組合成回應；計算畫面上的逾期狀態與可取消提示。
 * 建立回應只整理顯示資料，不會因此修改資料庫中的報名狀態。
 */
@Getter
public class RegistrationResponse {

    private Long registrationId;
    private Long memberId;
    private Long eventId;
    private String status;
    private LocalDateTime registeredAt;
    private LocalDateTime cancelledAt;

    private String eventTitle;
    private LocalDateTime startAt;
    private String location;
    private String eventStatus;
    private LocalDateTime registrationDeadline;
    private boolean canCancel;
    private LocalDateTime paymentExpiresAt;
    private String paymentStatus;

    public RegistrationResponse(Registration registration, Event event) {
        this(registration, event, null);
    }

    public RegistrationResponse(Registration registration, Event event, String paymentStatus) {
        this.paymentStatus = paymentStatus;
        this.registrationId = registration.getId();
        this.memberId = registration.getMemberId();
        this.eventId = registration.getEventId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        this.status = registration.getStatus();
        this.paymentExpiresAt = registration.getPaymentExpiresAt();
        // 查詢時即可顯示逾期，無須等待背景排程寫入狀態。
        if ("PENDING_PAYMENT".equals(this.status) && !now.isBefore(paymentExpiresAt)) {
            this.status = "EXPIRED";
        }
        this.registeredAt = registration.getRegisteredAt();
        this.cancelledAt = registration.getCancelledAt();
        if (event != null) {
            this.eventTitle = event.getTitle();
            this.startAt = event.getStartAt();
            this.location = event.getLocation();
            this.eventStatus = event.getStatus();
            this.registrationDeadline = event.getRegistrationDeadline();
            // 此欄位供畫面提示；真正取消時，Service 仍會重新檢查資格與截止時間。
            this.canCancel = ("REGISTERED".equals(this.status) || "PENDING_PAYMENT".equals(this.status))
                    && now.isBefore(event.getRegistrationDeadline());
        }
    }
}