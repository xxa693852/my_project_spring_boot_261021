package student.jia.my_project_spring_boot_261021.response;

import java.time.LocalDateTime;

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
    private String status;

    public EventResponse(Event event) {
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
        this.status = event.getStatus();
    }
}