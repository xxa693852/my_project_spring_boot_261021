package student.jia.my_project_spring_boot_261021.response;

import java.time.LocalDateTime;

import lombok.Getter;
import student.jia.my_project_spring_boot_261021.entity.Registration;

@Getter
public class RegistrationResponse {

    private Long registrationId;
    private Long memberId;
    private Long eventId;
    private String status;
    private LocalDateTime registeredAt;
    private LocalDateTime cancelledAt;

    public RegistrationResponse(Registration registration) {
        this.registrationId = registration.getId();
        this.memberId = registration.getMemberId();
        this.eventId = registration.getEventId();
        this.status = registration.getStatus();
        this.registeredAt = registration.getRegisteredAt();
        this.cancelledAt = registration.getCancelledAt();
    }
}