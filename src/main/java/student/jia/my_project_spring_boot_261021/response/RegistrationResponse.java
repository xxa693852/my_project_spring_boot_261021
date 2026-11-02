package student.jia.my_project_spring_boot_261021.response;

import java.time.LocalDateTime;

import lombok.Getter;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.entity.Event;

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

    public RegistrationResponse(Registration registration, Event event) {
        this.registrationId = registration.getId();
        this.memberId = registration.getMemberId();
        this.eventId = registration.getEventId();
        this.status = registration.getStatus();
        this.registeredAt = registration.getRegisteredAt();
        this.cancelledAt = registration.getCancelledAt();
        if (event != null) {
            this.eventTitle = event.getTitle();
            this.startAt = event.getStartAt();
            this.location = event.getLocation();
            this.eventStatus = event.getStatus();
            this.registrationDeadline = event.getRegistrationDeadline();
            // The server remains the authority for cancellation eligibility.
            this.canCancel = "REGISTERED".equals(registration.getStatus())
                    && LocalDateTime.now().isBefore(event.getRegistrationDeadline());
        }
    }
}